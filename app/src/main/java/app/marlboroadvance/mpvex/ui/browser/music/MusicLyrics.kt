package app.marlboroadvance.mpvex.ui.browser.music

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ---------------------------------------------------------------------------------------------
// Lyrics model + storage + web search (LRCLIB, free, no account needed)
// ---------------------------------------------------------------------------------------------

internal data class LyricLine(
  val timeMs: Long,
  val text: String,
)

internal class LyricsData(
  raw: String,
) {
  val lines: List<LyricLine>
  val synced: Boolean

  init {
    val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    val tag = Regex("^\\[[a-zA-Z]+:.*]$")
    val out = mutableListOf<LyricLine>()
    for (line in raw.lines()) {
      val l = line.trim()
      if (tag.matches(l)) continue
      val stamps = stamp.findAll(l).toList()
      if (stamps.isEmpty()) {
        out.add(LyricLine(-1, l))
      } else {
        val text = stamp.replace(l, "").trim()
        for (m in stamps) {
          val min = m.groupValues[1].toLong()
          val sec = m.groupValues[2].toLong()
          val frac = m.groupValues[3]
          val ms = if (frac.isEmpty()) 0L else (frac.padEnd(3, '0').take(3)).toLong()
          out.add(LyricLine(min * 60_000 + sec * 1000 + ms, text))
        }
      }
    }
    synced = out.count { it.timeMs >= 0 } >= 2
    lines = if (synced) out.filter { it.timeMs >= 0 }.sortedBy { it.timeMs } else out
  }
}

internal object LyricsStore {
  /** Bumped after every save so open lyrics views reload. */
  var version by mutableIntStateOf(0)

  private fun file(
    ctx: Context,
    id: Long,
  ) = File(File(ctx.filesDir, "lyrics"), "${if (id < 0) "x${-id}" else id.toString()}.lrc")

  fun load(
    ctx: Context,
    t: MusicTrack,
  ): String? {
    try {
      val f = file(ctx, t.id)
      if (f.exists()) return f.readText().takeIf { it.isNotBlank() }
      if (t.path.contains('.')) {
        val side = File(t.path.substringBeforeLast('.') + ".lrc")
        if (side.exists() && side.canRead()) return side.readText().takeIf { it.isNotBlank() }
      }
    } catch (_: Exception) {
    }
    return null
  }

  fun save(
    ctx: Context,
    t: MusicTrack,
    text: String,
  ) {
    try {
      val f = file(ctx, t.id)
      f.parentFile?.mkdirs()
      if (text.isBlank()) f.delete() else f.writeText(text)
      version++
    } catch (_: Exception) {
    }
  }
}

private fun JSONObject.lyricText(): String? {
  val synced = optString("syncedLyrics", "")
  if (synced.isNotBlank() && synced != "null") return synced
  val plain = optString("plainLyrics", "")
  if (plain.isNotBlank() && plain != "null") return plain
  return null
}

/** Searches lrclib.net for the song. Returns synced (LRC) lyrics when available, else plain text. */
internal suspend fun fetchLyricsOnline(t: MusicTrack): String? =
  withContext(Dispatchers.IO) {
    fun get(url: String): String? {
      val c = URL(url).openConnection() as HttpURLConnection
      return try {
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "OnlyPlay Music")
        if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
      } catch (_: Exception) {
        null
      } finally {
        c.disconnect()
      }
    }

    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    val artist = if (t.artist.startsWith("Unknown")) "" else t.artist
    val album = if (t.album.startsWith("Unknown")) "" else t.album
    try {
      if (artist.isNotEmpty()) {
        get("https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(t.title)}&album_name=${enc(album)}&duration=${t.durationMs / 1000}")
          ?.let { JSONObject(it).lyricText() }
          ?.let { return@withContext it }
      }
      for (q in listOf("$artist ${t.title}".trim(), t.title)) {
        val body = get("https://lrclib.net/api/search?q=${enc(q)}") ?: continue
        val arr = JSONArray(body)
        var plainFallback: String? = null
        for (i in 0 until arr.length()) {
          val o = arr.getJSONObject(i)
          val s = o.optString("syncedLyrics", "")
          if (s.isNotBlank() && s != "null") return@withContext s
          if (plainFallback == null) plainFallback = o.lyricText()
        }
        if (plainFallback != null) return@withContext plainFallback
      }
    } catch (_: Exception) {
    }
    null
  }

// ---------------------------------------------------------------------------------------------
// Lyrics panel (shown instead of the cover) + editor screen
// ---------------------------------------------------------------------------------------------

@Composable
internal fun LyricsPanel(
  track: MusicTrack,
  pos: Int,
  onSeek: (Int) -> Unit,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  var raw by remember(track.id) { mutableStateOf<String?>(null) }
  var busy by remember(track.id) { mutableStateOf(true) }
  var failed by remember(track.id) { mutableStateOf(false) }

  fun search() {
    scope.launch {
      busy = true
      failed = false
      val r = fetchLyricsOnline(track)
      if (r != null) {
        LyricsStore.save(ctx, track, r)
        raw = r
      } else {
        failed = true
      }
      busy = false
    }
  }

  LaunchedEffect(track.id, LyricsStore.version) {
    busy = true
    raw = withContext(Dispatchers.IO) { LyricsStore.load(ctx, track) ?: readEmbeddedLyrics(ctx, track) }
    busy = false
    if (raw == null && MusicSettings.lyricsAuto) search()
  }

  Box(modifier = modifier) {
    val text = raw
    if (text == null) {
      Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        if (busy) {
          CircularProgressIndicator()
          Spacer(Modifier.height(12.dp))
          Text("Searching lyrics online…", style = MaterialTheme.typography.titleMedium)
        } else {
          Text(if (failed) "No lyrics found" else "No embedded lyrics found", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
          Text(
            "Tap to edit",
            style = MaterialTheme.typography.titleMedium,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onEdit).padding(8.dp),
          )
          TextButton(onClick = { search() }) { Text("Search online") }
        }
      }
    } else {
      val data = remember(text) { LyricsData(text) }
      val state = rememberLazyListState()
      val current = if (data.synced) data.lines.indexOfLast { it.timeMs <= pos + 250 } else -1
      LaunchedEffect(current) {
        if (current >= 0) state.animateScrollToItem(current)
      }
      LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        itemsIndexed(data.lines) { i, l ->
          val active = !data.synced || i == current
          Text(
            text = l.text.ifEmpty { "♪" },
            modifier =
              Modifier
                .fillMaxWidth()
                .then(if (data.synced) Modifier.clickable { onSeek(l.timeMs.toInt()) } else Modifier),
            textAlign = TextAlign.Center,
            style = if (data.synced && i == current) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = if (data.synced && i == current) FontWeight.Bold else MusicFont.normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 1f else 0.45f),
          )
        }
      }
    }
  }
}

@Composable
internal fun LyricsEditor(
  track: MusicTrack,
  onClose: () -> Unit,
) {
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  var tf by remember(track.id) { mutableStateOf(TextFieldValue("")) }
  var busy by remember { mutableStateOf(false) }
  LaunchedEffect(track.id) {
    val t = withContext(Dispatchers.IO) { LyricsStore.load(ctx, track) } ?: ""
    tf = TextFieldValue(t)
  }
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text("Edit lyrics", style = MaterialTheme.typography.titleLarge)
      }
      Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TrackArt(track, 200, Modifier.size(80.dp), RoundedCornerShape(14.dp))
        Column {
          Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(track.artist, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Surface(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        TextField(
          value = tf,
          onValueChange = { tf = it },
          modifier = Modifier.fillMaxSize(),
          placeholder = { Text("Paste synced or normal lyrics here") },
          colors =
            TextFieldDefaults.colors(
              focusedContainerColor = Color.Transparent,
              unfocusedContainerColor = Color.Transparent,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
            ),
        )
      }
      Spacer(Modifier.height(12.dp))
      Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
          IconButton(
            onClick = {
              try {
                val q = URLEncoder.encode("${track.artist} ${track.title} lyrics", "UTF-8")
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
              } catch (_: Exception) {
              }
            },
          ) { Icon(Icons.Filled.Public, "Search on the web") }
          IconButton(
            enabled = !busy,
            onClick = {
              scope.launch {
                busy = true
                val r = fetchLyricsOnline(track)
                busy = false
                if (r != null) tf = TextFieldValue(r) else Toast.makeText(ctx, "No lyrics found online", Toast.LENGTH_SHORT).show()
              }
            },
          ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Download, "Download lyrics") }
          IconButton(
            onClick = {
              val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
              if (!clip.isNullOrEmpty()) {
                val s = tf.selection
                val newText = tf.text.replaceRange(s.min, s.max, clip)
                tf = TextFieldValue(newText, TextRange(s.min + clip.length))
              }
            },
          ) { Icon(Icons.Filled.ContentPaste, "Paste") }
          IconButton(onClick = { tf = tf.copy(selection = TextRange(0, tf.text.length)) }) { Icon(Icons.Filled.SelectAll, "Select all") }
          IconButton(
            onClick = {
              LyricsStore.save(ctx, track, tf.text)
              Toast.makeText(ctx, "Lyrics saved", Toast.LENGTH_SHORT).show()
              onClose()
            },
          ) { Icon(Icons.Filled.Save, "Save") }
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Misc helpers: song info line and files added by hand
// ---------------------------------------------------------------------------------------------

/** "M4A  •  129 kb/s  •  48.0 kHz" */
internal fun loadSongInfo(
  ctx: Context,
  t: MusicTrack,
): String {
  val parts = mutableListOf<String>()
  val ext = t.path.substringAfterLast('.', "")
  if (ext.isNotEmpty() && ext.length <= 5) parts.add(ext.uppercase())
  val r = MediaMetadataRetriever()
  try {
    r.setDataSource(ctx, t.uri)
    if (ext.isEmpty()) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.substringAfter('/')?.let { parts.add(it.uppercase()) }
    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { parts.add("${it / 1000} kb/s") }
  } catch (_: Exception) {
  } finally {
    try {
      r.release()
    } catch (_: Exception) {
    }
  }
  val ex = MediaExtractor()
  try {
    ex.setDataSource(ctx, t.uri, null)
    if (ex.trackCount > 0) {
      val f = ex.getTrackFormat(0)
      if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) parts.add("%.1f kHz".format(f.getInteger(MediaFormat.KEY_SAMPLE_RATE) / 1000f))
    }
  } catch (_: Exception) {
  } finally {
    try {
      ex.release()
    } catch (_: Exception) {
    }
  }
  return parts.joinToString("  •  ")
}

/** Builds a track from a file picked with "Add files". */
internal fun trackFromUri(
  ctx: Context,
  uri: Uri,
): MusicTrack? {
  try {
    ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  } catch (_: Exception) {
  }
  var name = uri.lastPathSegment ?: "Audio"
  try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) name = it.getString(0) ?: name
    }
  } catch (_: Exception) {
  }
  var title = name.substringBeforeLast('.')
  var artist = "Unknown artist"
  var album = "Unknown album"
  var dur = 0L
  val r = MediaMetadataRetriever()
  try {
    r.setDataSource(ctx, uri)
    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
    dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
  } catch (_: Exception) {
    return null
  } finally {
    try {
      r.release()
    } catch (_: Exception) {
    }
  }
  val id = -((uri.toString().hashCode().toLong() and 0x7fffffffL) + 1)
  return MusicTrack(id, title, artist, album, 0L, dur, System.currentTimeMillis() / 1000, "", uri)
}

// ---------------------------------------------------------------------------------------------
// Lyrics stored inside the audio file (MP3 "USLT" tag, M4A/MP4 "©lyr" tag)
// ---------------------------------------------------------------------------------------------

internal fun readEmbeddedLyrics(
  ctx: Context,
  t: MusicTrack,
): String? =
  try {
    ctx.contentResolver.openFileDescriptor(t.uri, "r")?.use { pfd ->
      val ch = FileInputStream(pfd.fileDescriptor).channel
      val head = ByteBuffer.allocate(12)
      ch.read(head, 0)
      val b = head.array()
      when {
        b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte() -> readId3Lyrics(ch)
        b[4] == 'f'.code.toByte() && b[5] == 't'.code.toByte() && b[6] == 'y'.code.toByte() && b[7] == 'p'.code.toByte() -> readMp4Lyrics(ch)
        else -> null
      }
    }
  } catch (_: Exception) {
    null
  }

private fun beInt(
  a: ByteArray,
  o: Int,
): Int = ((a[o].toInt() and 0xFF) shl 24) or ((a[o + 1].toInt() and 0xFF) shl 16) or ((a[o + 2].toInt() and 0xFF) shl 8) or (a[o + 3].toInt() and 0xFF)

private fun synchsafe(
  a: ByteArray,
  o: Int,
): Int = ((a[o].toInt() and 0x7F) shl 21) or ((a[o + 1].toInt() and 0x7F) shl 14) or ((a[o + 2].toInt() and 0x7F) shl 7) or (a[o + 3].toInt() and 0x7F)

private fun readFully(
  ch: FileChannel,
  buf: ByteBuffer,
  from: Long,
) {
  var p = from
  while (buf.hasRemaining()) {
    val n = ch.read(buf, p)
    if (n <= 0) break
    p += n
  }
}

private fun readId3Lyrics(ch: FileChannel): String? {
  val h = ByteBuffer.allocate(10)
  readFully(ch, h, 0)
  val a = h.array()
  val ver = a[3].toInt()
  val tagSize = synchsafe(a, 6)
  if (tagSize <= 0) return null
  val buf = ByteBuffer.allocate(minOf(tagSize, 8 * 1024 * 1024))
  readFully(ch, buf, 10)
  val d = buf.array()
  var p = 0
  while (p + 10 <= d.size) {
    if (d[p].toInt() == 0) break
    val id = String(d, p, 4, Charsets.ISO_8859_1)
    val size = if (ver == 4) synchsafe(d, p + 4) else beInt(d, p + 4)
    if (size <= 0 || p + 10 + size > d.size) break
    if (id == "USLT") return decodeUslt(d, p + 10, size)
    p += 10 + size
  }
  return null
}

private fun decodeUslt(
  d: ByteArray,
  off: Int,
  len: Int,
): String? {
  if (len < 5) return null
  val enc = d[off].toInt()
  val start = off + 4
  val end = off + len
  var textStart = start
  if (enc == 0 || enc == 3) {
    while (textStart < end && d[textStart].toInt() != 0) textStart++
    textStart += 1
  } else {
    while (textStart + 1 < end && !(d[textStart].toInt() == 0 && d[textStart + 1].toInt() == 0)) textStart += 2
    textStart += 2
  }
  if (textStart >= end) return null
  val cs =
    when (enc) {
      1 -> Charsets.UTF_16
      2 -> Charsets.UTF_16BE
      3 -> Charsets.UTF_8
      else -> Charsets.ISO_8859_1
    }
  return String(d, textStart, end - textStart, cs).trim().takeIf { it.isNotBlank() }
}

private fun readMp4Lyrics(ch: FileChannel): String? {
  val size = ch.size()
  var pos = 0L
  while (pos + 8 <= size) {
    val hdr = ByteBuffer.allocate(16)
    readFully(ch, hdr, pos)
    val h = hdr.array()
    var boxSize = beInt(h, 0).toLong() and 0xFFFFFFFFL
    val type = String(h, 4, 4, Charsets.ISO_8859_1)
    var header = 8L
    if (boxSize == 1L) {
      boxSize = ByteBuffer.wrap(h, 8, 8).long
      header = 16L
    } else if (boxSize == 0L) {
      boxSize = size - pos
    }
    if (boxSize < header) return null
    if (type == "moov" && boxSize - header < 32L * 1024 * 1024) {
      val buf = ByteBuffer.allocate((boxSize - header).toInt())
      readFully(ch, buf, pos + header)
      return findMp4Lyrics(buf.array())
    }
    pos += boxSize
  }
  return null
}

private fun findMp4Lyrics(a: ByteArray): String? {
  var i = 0
  while (i + 24 < a.size) {
    if (a[i] == 0xA9.toByte() && a[i + 1] == 'l'.code.toByte() && a[i + 2] == 'y'.code.toByte() && a[i + 3] == 'r'.code.toByte()) {
      val dataSize = beInt(a, i + 4)
      val tag = String(a, i + 8, 4, Charsets.ISO_8859_1)
      if (tag == "data" && dataSize > 16 && i + 4 + dataSize <= a.size) {
        return String(a, i + 4 + 16, dataSize - 16, Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
      }
    }
    i++
  }
  return null
}
