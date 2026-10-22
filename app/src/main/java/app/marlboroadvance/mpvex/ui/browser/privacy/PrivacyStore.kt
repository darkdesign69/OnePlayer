package app.marlboroadvance.mpvex.ui.browser.privacy

import android.content.Context
import android.media.MediaScannerConnection
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A video that was hidden through Privacy. */
data class HiddenVideo(
  val id: String,
  val name: String,
  val originalPath: String,
  val hiddenPath: String,
  val size: Long,
)

/**
 * Hides videos by moving them into the app's own private folder (no gallery or other app can see
 * it there) and remembers where each one came from so it can be restored.
 */
object PrivacyStore {
  private fun indexFile(context: Context): File = File(context.filesDir, "privacy_index.json")

  fun privateDir(context: Context): File {
    val dir = context.getExternalFilesDir("privacy") ?: File(context.filesDir, "privacy")
    dir.mkdirs()
    try {
      File(dir, ".nomedia").createNewFile()
    } catch (e: Exception) {
      // not important: the folder is already private to this app
    }
    return dir
  }

  fun load(context: Context): List<HiddenVideo> {
    val file = indexFile(context)
    if (!file.exists()) return emptyList()
    return try {
      val array = JSONArray(file.readText())
      val result = mutableListOf<HiddenVideo>()
      for (i in 0 until array.length()) {
        val o = array.getJSONObject(i)
        val video =
          HiddenVideo(
            id = o.getString("id"),
            name = o.getString("name"),
            originalPath = o.getString("originalPath"),
            hiddenPath = o.getString("hiddenPath"),
            size = o.optLong("size", 0L),
          )
        if (File(video.hiddenPath).isFile) result.add(video)
      }
      result
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun save(
    context: Context,
    list: List<HiddenVideo>,
  ) {
    val array = JSONArray()
    list.forEach { v ->
      array.put(
        JSONObject()
          .put("id", v.id)
          .put("name", v.name)
          .put("originalPath", v.originalPath)
          .put("hiddenPath", v.hiddenPath)
          .put("size", v.size),
      )
    }
    indexFile(context).writeText(array.toString())
  }

  private fun uniqueFile(
    dir: File,
    name: String,
  ): File {
    var candidate = File(dir, name)
    if (!candidate.exists()) return candidate
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var n = 1
    while (candidate.exists()) {
      candidate = File(dir, "$base ($n)$ext")
      n++
    }
    return candidate
  }

  /** Fast rename when possible, otherwise copy and delete. Returns true when the file was moved. */
  private fun moveFile(
    src: File,
    dest: File,
  ): Boolean {
    if (src.renameTo(dest)) return true
    return try {
      src.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
      if (dest.length() == src.length() && src.delete()) {
        true
      } else {
        dest.delete()
        false
      }
    } catch (e: Exception) {
      dest.delete()
      false
    }
  }

  private fun rescan(
    context: Context,
    paths: List<String>,
  ) {
    if (paths.isEmpty()) return
    MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
      MediaLibraryEvents.notifyChanged()
    }
  }

  /** Hides the videos at [paths]. Returns how many were hidden. */
  suspend fun hide(
    context: Context,
    paths: List<String>,
  ): Int =
    withContext(Dispatchers.IO) {
      val dir = privateDir(context)
      val index = load(context).toMutableList()
      val moved = mutableListOf<String>()
      for (path in paths) {
        val src = File(path)
        if (!src.isFile) continue
        val dest = uniqueFile(dir, src.name)
        val size = src.length()
        if (moveFile(src, dest)) {
          index.add(HiddenVideo(UUID.randomUUID().toString(), src.name, path, dest.absolutePath, size))
          moved.add(path)
        }
      }
      save(context, index)
      rescan(context, moved)
      moved.size
    }

  /** Puts a hidden video back where it came from. */
  suspend fun unhide(
    context: Context,
    video: HiddenVideo,
  ): Boolean =
    withContext(Dispatchers.IO) {
      val src = File(video.hiddenPath)
      val original = File(video.originalPath)
      val parent = original.parentFile ?: return@withContext false
      if (!src.isFile) {
        save(context, load(context).filter { it.id != video.id })
        return@withContext false
      }
      parent.mkdirs()
      val dest = if (original.exists()) uniqueFile(parent, original.name) else original
      val ok = moveFile(src, dest)
      if (ok) {
        save(context, load(context).filter { it.id != video.id })
        rescan(context, listOf(dest.absolutePath))
      }
      ok
    }

  /** Permanently deletes hidden videos. Returns how many were deleted. */
  suspend fun delete(
    context: Context,
    videos: List<HiddenVideo>,
  ): Int =
    withContext(Dispatchers.IO) {
      var count = 0
      videos.forEach { v ->
        val f = File(v.hiddenPath)
        if (!f.exists() || f.delete()) count++
      }
      val ids = videos.map { it.id }.toSet()
      save(context, load(context).filter { it.id !in ids })
      count
    }

  /** Renames a hidden video. It also comes back with the new name when it is restored. */
  suspend fun rename(
    context: Context,
    video: HiddenVideo,
    newName: String,
  ): Boolean =
    withContext(Dispatchers.IO) {
      var name = newName.trim().replace('/', '_')
      if (name.isEmpty()) return@withContext false
      val oldExt = video.name.substringAfterLast('.', "")
      if (!name.contains('.') && oldExt.isNotEmpty()) name = "$name.$oldExt"
      if (name == video.name) return@withContext true
      val src = File(video.hiddenPath)
      if (!src.isFile) return@withContext false
      val dest = File(src.parentFile, name)
      if (dest.exists() || !src.renameTo(dest)) return@withContext false
      val original = File(video.originalPath)
      val newOriginal = File(original.parentFile, name).absolutePath
      val updated = load(context).map { if (it.id == video.id) it.copy(name = name, hiddenPath = dest.absolutePath, originalPath = newOriginal) else it }
      save(context, updated)
      true
    }
}
