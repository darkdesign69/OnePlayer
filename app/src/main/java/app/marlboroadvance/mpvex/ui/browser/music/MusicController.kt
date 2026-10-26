package app.marlboroadvance.mpvex.ui.browser.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.widget.MusicWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One audio file from MediaStore. */
data class MusicTrack(
  val id: Long,
  val title: String,
  val artist: String,
  val album: String,
  val albumId: Long,
  val durationMs: Long,
  val dateAdded: Long,
  val path: String,
  val uri: Uri,
)

/**
 * Everything the user changed in the Tag editor (title, artist, album, composer, year, cover ...).
 * Kept in the app and applied whenever the library loads.
 */
object MusicTagEdits {
  private const val PREFS = "music_tag_edits"

  private fun read(ctx: Context): JSONObject =
    try {
      JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("edits", "{}") ?: "{}")
    } catch (_: Exception) {
      JSONObject()
    }

  /** The saved edits of one song (empty when there are none). */
  fun get(
    ctx: Context,
    id: Long,
  ): JSONObject = read(ctx).optJSONObject(id.toString()) ?: JSONObject()

  fun apply(
    ctx: Context,
    tracks: List<MusicTrack>,
  ): List<MusicTrack> {
    val all = read(ctx)
    if (all.length() == 0) return tracks
    return tracks.map { t ->
      val e = all.optJSONObject(t.id.toString()) ?: return@map t
      t.copy(
        title = e.optString("title", t.title).ifBlank { t.title },
        artist = e.optString("artist", t.artist).ifBlank { t.artist },
        album = e.optString("album", t.album).ifBlank { t.album },
      )
    }
  }

  /** Remembers the new tags and also asks Android's media library to take them (works where the system allows it). */
  fun save(
    ctx: Context,
    track: MusicTrack,
    fields: Map<String, String>,
  ) {
    val all = read(ctx)
    val e = all.optJSONObject(track.id.toString()) ?: JSONObject()
    for ((k, v) in fields) e.put(k, v)
    all.put(track.id.toString(), e)
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("edits", all.toString()).apply()
    try {
      val v =
        android.content.ContentValues().apply {
          put(MediaStore.Audio.Media.TITLE, fields["title"] ?: track.title)
          put(MediaStore.Audio.Media.ARTIST, fields["artist"] ?: track.artist)
          put(MediaStore.Audio.Media.ALBUM, fields["album"] ?: track.album)
        }
      ctx.contentResolver.update(track.uri, v, null, null)
    } catch (_: Exception) {
      // not allowed on this Android version: the app's own copy above is used instead
    }
  }

  // ---- cover ----

  private fun coverFile(
    ctx: Context,
    id: Long,
  ) = java.io.File(java.io.File(ctx.filesDir, "tag_covers").apply { mkdirs() }, "$id.jpg")

  /** 0 = the song's own cover, 1 = a cover picked in the Tag editor, 2 = cover removed */
  fun coverState(
    ctx: Context,
    id: Long,
  ): Int {
    val e = read(ctx).optJSONObject(id.toString()) ?: return 0
    return when {
      e.optBoolean("coverRemoved", false) -> 2
      e.optBoolean("coverCustom", false) && coverFile(ctx, id).exists() -> 1
      else -> 0
    }
  }

  fun loadCover(
    ctx: Context,
    id: Long,
    size: Int,
  ): Bitmap? =
    try {
      val f = coverFile(ctx, id)
      val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(f.absolutePath, o)
      var sample = 1
      while (o.outWidth / (sample * 2) >= size) sample *= 2
      BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) {
      null
    }

  /** Copies a picked picture in as the song's cover (shrunk to at most 1024 px). */
  fun setCover(
    ctx: Context,
    id: Long,
    uri: Uri,
  ): Boolean =
    try {
      val src =
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return false
      val big = maxOf(src.width, src.height)
      val bmp = if (big > 1024) Bitmap.createScaledBitmap(src, src.width * 1024 / big, src.height * 1024 / big, true) else src
      coverFile(ctx, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
      mark(ctx, id, custom = true, removed = false)
      true
    } catch (_: Exception) {
      false
    }

  fun removeCover(
    ctx: Context,
    id: Long,
  ) {
    try {
      coverFile(ctx, id).delete()
    } catch (_: Exception) {
    }
    mark(ctx, id, custom = false, removed = true)
  }

  private fun mark(
    ctx: Context,
    id: Long,
    custom: Boolean,
    removed: Boolean,
  ) {
    val all = read(ctx)
    val e = all.optJSONObject(id.toString()) ?: JSONObject()
    e.put("coverCustom", custom)
    e.put("coverRemoved", removed)
    all.put(id.toString(), e)
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("edits", all.toString()).apply()
    artCache.evictAll()
  }
}

// ---------------------------------------------------------------------------------------------
// Album art (cached)
// ---------------------------------------------------------------------------------------------

private val artCache =
  object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(
      key: String,
      value: Bitmap,
    ): Int = value.byteCount
  }

fun loadArt(
  context: Context,
  track: MusicTrack,
  size: Int,
): Bitmap? {
  when (MusicTagEdits.coverState(context, track.id)) {
    2 -> return null
    1 -> {
      val ck = "c${track.id}_$size"
      artCache.get(ck)?.let { return it }
      val c = MusicTagEdits.loadCover(context, track.id, size)
      if (c != null) artCache.put(ck, c)
      return c
    }
  }
  val useStore = MusicSettings.useMediaStoreArt
  val key = (if (track.albumId > 0 && useStore) "a${track.albumId}" else "t${track.id}") + "_$size" + (if (useStore) "" else "_f")
  artCache.get(key)?.let { return it }
  var bmp: Bitmap? = null
  try {
    if (useStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      bmp = context.contentResolver.loadThumbnail(track.uri, Size(size, size), null)
    }
  } catch (_: Exception) {
  }
  if (useStore && bmp == null && track.albumId > 0) {
    try {
      val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), track.albumId)
      context.contentResolver.openInputStream(artUri)?.use { bmp = BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
    }
  }
  if (bmp == null && (track.id < 0 || !useStore)) {
    // files added by hand: read the cover stored inside the file
    val r = android.media.MediaMetadataRetriever()
    try {
      r.setDataSource(context, track.uri)
      val data = r.embeddedPicture
      if (data != null) bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
    } catch (_: Exception) {
    } finally {
      try {
        r.release()
      } catch (_: Exception) {
      }
    }
  }
  if (MusicSettings.cacheImages) bmp?.let { artCache.put(key, it) }
  return bmp
}

// ---------------------------------------------------------------------------------------------
// MediaStore loading
// ---------------------------------------------------------------------------------------------

fun loadMusicTracks(context: Context): List<MusicTrack> {
  val collection =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
  val projection =
    arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.ALBUM_ID,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.DATE_ADDED,
      MediaStore.Audio.Media.DATA,
    )
  val result = mutableListOf<MusicTrack>()
  try {
    context.contentResolver
      .query(
        collection,
        projection,
        "${MediaStore.Audio.Media.DURATION} >= ${MusicSettings.minDurationSec * 1000} AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0",
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
      )?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (c.moveToNext()) {
          val id = c.getLong(iId)
          val artist = c.getString(iArtist)
          val album = c.getString(iAlbum)
          result.add(
            MusicTrack(
              id = id,
              title = c.getString(iTitle) ?: "Unknown title",
              artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Unknown artist" else artist,
              album = if (album.isNullOrBlank()) "Unknown album" else album,
              albumId = c.getLong(iAlbumId),
              durationMs = c.getLong(iDur),
              dateAdded = c.getLong(iAdded),
              path = c.getString(iData) ?: "",
              uri = ContentUris.withAppendedId(collection, id),
            ),
          )
        }
      }
  } catch (_: Exception) {
    // empty list => "No music found"
  }
  val edited = MusicTagEdits.apply(context, result)
  val excluded = MusicSettings.excluded()
  if (excluded.isNotEmpty()) return edited.filter { t -> excluded.none { t.path.startsWith(it) } }
  return edited
}

// ---------------------------------------------------------------------------------------------
// Controller: queue, MediaPlayer, favorites, history, playlists, sleep timer, session
// ---------------------------------------------------------------------------------------------

object MusicController {
  private const val PREFS = "retro_music"
  private const val CHANNEL_ID = "music_playback"
  const val NOTIF_ID = 4711

  private lateinit var app: Context
  private var initialised = false
  private var player: MediaPlayer? = null
  private var prepared = false
  private var session: MediaSessionCompat? = null
  private var focusRequest: AudioFocusRequest? = null
  private var resumeOnFocus = false
  private var baseQueue: List<MusicTrack> = emptyList()
  private var notifArt: Bitmap? = null
  private var restoredTrackId = -1L
  private var restoredPosMs = 0
  private val handler = Handler(Looper.getMainLooper())
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  var serviceRunning = false

  val queue = mutableStateListOf<MusicTrack>()

  /** Goes up when a song's tags were edited, so the library lists reload. */
  var libraryVersion by mutableIntStateOf(0)
  var index by mutableIntStateOf(-1)
  var isPlaying by mutableStateOf(false)
  var shuffle by mutableStateOf(false)

  /** 0 = off, 1 = repeat all, 2 = repeat one */
  var repeat by mutableIntStateOf(0)
  var speed by mutableFloatStateOf(1f)
  var sleepEndAt by mutableLongStateOf(0L)
  var favorites by mutableStateOf<Set<Long>>(emptySet())
  var history by mutableStateOf<List<Long>>(emptyList())
  var playlists by mutableStateOf<Map<String, List<Long>>>(emptyMap())

  val current: MusicTrack? get() = queue.getOrNull(index)
  val audioSessionId: Int get() = player?.audioSessionId ?: 0

  private val noisyReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(
        context: Context?,
        intent: Intent?,
      ) {
        if (isPlaying && MusicSettings.pauseOnUnplug) pause()
      }
    }

  private val sleepRunnable =
    Runnable {
      sleepEndAt = 0L
      pause()
    }

  fun init(context: Context) {
    if (initialised) return
    initialised = true
    app = context.applicationContext
    MusicSettings.init(app)
    loadPrefs()
    registerDeviceCallback()
    session =
      MediaSessionCompat(app, "OnlyPlayMusic").apply {
        setCallback(
          object : MediaSessionCompat.Callback() {
            override fun onPlay() = resume()

            override fun onPause() = pause()

            override fun onSkipToNext() = next()

            override fun onSkipToPrevious() = previous()

            override fun onSeekTo(pos: Long) = seekTo(pos.toInt())

            override fun onStop() = stopAll()
          },
        )
      }
    ContextCompat.registerReceiver(
      app,
      noisyReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
      ContextCompat.RECEIVER_EXPORTED,
    )
  }

  // ---- playback ----

  fun play(
    list: List<MusicTrack>,
    start: Int,
    shuffleOn: Boolean = shuffle,
  ) {
    if (list.isEmpty()) return
    baseQueue = list
    shuffle = shuffleOn
    val first = list[start.coerceIn(list.indices)]
    queue.clear()
    if (shuffle) {
      queue.add(first)
      queue.addAll(list.filter { it.id != first.id }.shuffled())
      index = 0
    } else {
      queue.addAll(list)
      index = list.indexOfFirst { it.id == first.id }.coerceAtLeast(0)
    }
    startCurrent()
  }

  fun shuffleAll(list: List<MusicTrack>) {
    if (list.isEmpty()) return
    play(list, (list.indices).random(), true)
  }

  fun playAt(i: Int) {
    if (i !in queue.indices) return
    index = i
    startCurrent()
  }

  private fun startCurrent() {
    val t = current ?: return
    val seekOnStart = if (restoredTrackId == t.id) restoredPosMs else 0
    restoredTrackId = -1L
    restoredPosMs = 0
    releasePlayer()
    if (!requestFocus()) return
    val mp = MediaPlayer()
    player = mp
    prepared = false
    try {
      mp.setAudioAttributes(
        AudioAttributes
          .Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .build(),
      )
      mp.setWakeMode(app, android.os.PowerManager.PARTIAL_WAKE_LOCK)
      mp.setDataSource(app, t.uri)
      mp.setOnPreparedListener {
        prepared = true
        applySpeed()
        if (seekOnStart > 0) it.seekTo(seekOnStart)
        it.start()
        isPlaying = true
        fadeIn()
        onStateChanged()
      }
      mp.setOnCompletionListener { onComplete() }
      mp.setOnErrorListener { _, _, _ ->
        next(true)
        true
      }
      mp.prepareAsync()
    } catch (_: Exception) {
      next(true)
      return
    }
    addHistory(t.id)
    session?.isActive = true
    isPlaying = true
    onStateChanged()
    notifArt = null
    scope.launch {
      val b = withContext(Dispatchers.IO) { loadArt(app, t, 512) }
      if (current?.id == t.id) {
        notifArt = b
        onStateChanged()
      }
    }
  }

  private fun onComplete() {
    if (repeat == 2) {
      player?.seekTo(0)
      player?.start()
    } else {
      next(true)
    }
  }

  fun resume() {
    if (player == null) {
      // Queue was brought back after reopening the app: start it from the saved position
      if (current != null) startCurrent()
      return
    }
    val mp = player ?: return
    if (!prepared) return
    if (!requestFocus()) return
    cancelFade()
    mp.start()
    isPlaying = true
    fadeIn()
    onStateChanged()
  }

  fun pause() {
    val mp = player ?: return
    if (prepared && mp.isPlaying) {
      if (MusicSettings.audioFade) {
        isPlaying = false
        fade(1f, 0f) {
          try {
            mp.pause()
            mp.setVolume(1f, 1f)
          } catch (_: Exception) {
          }
        }
        onStateChanged()
        return
      }
      mp.pause()
    }
    isPlaying = false
    onStateChanged()
  }

  private var fadeRunnable: Runnable? = null

  private fun cancelFade() {
    fadeRunnable?.let { handler.removeCallbacks(it) }
    fadeRunnable = null
    try {
      player?.setVolume(1f, 1f)
    } catch (_: Exception) {
    }
  }

  private fun fadeIn() {
    if (MusicSettings.audioFade) fade(0f, 1f, null)
  }

  private fun fade(
    from: Float,
    to: Float,
    then: (() -> Unit)?,
  ) {
    fadeRunnable?.let { handler.removeCallbacks(it) }
    val mp = player ?: return
    var v = from
    val step = if (to > from) 0.1f else -0.1f
    try {
      mp.setVolume(from, from)
    } catch (_: Exception) {
    }
    val r =
      object : Runnable {
        override fun run() {
          v += step
          val c = v.coerceIn(0f, 1f)
          try {
            player?.setVolume(c, c)
          } catch (_: Exception) {
          }
          val more = if (step > 0) v < to else v > to
          if (more) {
            handler.postDelayed(this, 40)
          } else {
            fadeRunnable = null
            then?.invoke()
          }
        }
      }
    fadeRunnable = r
    handler.post(r)
  }

  private fun registerDeviceCallback() {
    try {
      val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val t0 = System.currentTimeMillis()
      am.registerAudioDeviceCallback(
        object : android.media.AudioDeviceCallback() {
          override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) {
            if (System.currentTimeMillis() - t0 < 1500) return
            if (!MusicSettings.autoPlay || isPlaying || queue.isEmpty()) return
            val ok =
              added.any {
                it.isSink &&
                  (
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                      it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                      it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                      it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                  )
              }
            if (ok) resume()
          }
        },
        handler,
      )
    } catch (_: Exception) {
    }
  }

  private fun fakeBlur(b: Bitmap): Bitmap =
    try {
      Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(b, 24, 24, true), 512, 512, true)
    } catch (_: Exception) {
      b
    }

  fun togglePlay() {
    if (isPlaying) pause() else resume()
  }

  fun next(auto: Boolean = false) {
    if (queue.isEmpty()) return
    if (index < queue.lastIndex) {
      index++
      startCurrent()
    } else if (repeat == 1 || !auto) {
      index = 0
      startCurrent()
    } else {
      // end of queue
      player?.let { if (prepared) it.seekTo(0) }
      pause()
    }
  }

  fun previous() {
    if (queue.isEmpty()) return
    if (position() > 3000) {
      seekTo(0)
    } else if (index > 0) {
      index--
      startCurrent()
    } else if (repeat == 1) {
      index = queue.lastIndex
      startCurrent()
    } else {
      seekTo(0)
    }
  }

  fun position(): Int =
    when {
      prepared -> player?.currentPosition ?: 0
      player == null && current != null && restoredTrackId == current?.id -> restoredPosMs
      else -> 0
    }

  fun seekTo(ms: Int) {
    if (prepared) {
      player?.seekTo(ms)
      onStateChanged()
    } else if (player == null) {
      current?.let {
        restoredTrackId = it.id
        restoredPosMs = ms
      }
    }
  }

  fun toggleShuffle() {
    shuffle = !shuffle
    reorderForShuffle()
    if (initialised) {
      app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("q_shuffle", shuffle).apply()
      saveQueueState()
      MusicWidgetUpdater.update(app)
    }
  }

  /** Sets shuffle on or off (used by the home screen widget, also works when nothing is loaded). */
  fun setShuffleMode(on: Boolean) {
    if (shuffle != on) {
      toggleShuffle()
    } else if (initialised) {
      app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("q_shuffle", on).apply()
      MusicWidgetUpdater.update(app)
    }
  }

  private fun reorderForShuffle() {
    val cur = current ?: return
    if (shuffle) {
      val rest = queue.filter { it.id != cur.id }.shuffled()
      queue.clear()
      queue.add(cur)
      queue.addAll(rest)
      index = 0
    } else {
      val ordered = if (baseQueue.isNotEmpty()) baseQueue else queue.toList()
      queue.clear()
      queue.addAll(ordered)
      index = ordered.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)
    }
  }

  fun cycleRepeat() = setRepeatMode((repeat + 1) % 3)

  /** 0 = off, 1 = repeat all, 2 = repeat one. Also used by the home screen widget. */
  fun setRepeatMode(mode: Int) {
    repeat = mode.coerceIn(0, 2)
    if (initialised) {
      app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("q_repeat", repeat).apply()
      saveQueueState()
      MusicWidgetUpdater.update(app)
    }
  }

  fun changeSpeed(value: Float) {
    speed = value
    applySpeed()
    onStateChanged()
  }

  private fun applySpeed() {
    val mp = player ?: return
    if (!prepared) return
    try {
      val wasPlaying = mp.isPlaying
      mp.playbackParams = PlaybackParams().setSpeed(speed)
      // setting params can start playback on some devices
      if (!wasPlaying && mp.isPlaying) mp.pause()
    } catch (_: Exception) {
    }
  }

  // ---- queue editing ----

  fun playNext(track: MusicTrack) {
    if (queue.isEmpty()) {
      play(listOf(track), 0)
      return
    }
    if (queue.any { it.id == track.id }) queue.removeAll { it.id == track.id && it.id != current?.id }
    val pos = (queue.indexOfFirst { it.id == current?.id } + 1).coerceIn(0, queue.size)
    queue.add(pos, track)
    index = queue.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val b = baseQueue.toMutableList()
    b.removeAll { it.id == track.id }
    val bi = (b.indexOfFirst { it.id == current?.id } + 1).coerceIn(0, b.size)
    b.add(bi, track)
    baseQueue = b
  }

  fun addToQueue(track: MusicTrack) {
    if (queue.isEmpty()) {
      play(listOf(track), 0)
      return
    }
    if (queue.any { it.id == track.id }) return
    queue.add(track)
    baseQueue = baseQueue + track
  }

  fun removeFromQueue(i: Int) {
    if (i !in queue.indices) return
    val removed = queue[i]
    if (queue.size == 1) {
      stopAll()
      return
    }
    queue.removeAt(i)
    baseQueue = baseQueue.filter { it.id != removed.id }
    if (i == index) {
      if (index > queue.lastIndex) index = queue.lastIndex
      startCurrent()
    } else if (i < index) {
      index--
    }
  }

  fun clearQueue() {
    val cur = current ?: return
    queue.clear()
    queue.add(cur)
    index = 0
    baseQueue = listOf(cur)
  }

  // ---- sleep timer ----

  fun setSleepTimer(minutes: Int) {
    handler.removeCallbacks(sleepRunnable)
    if (minutes <= 0) {
      sleepEndAt = 0L
      return
    }
    sleepEndAt = System.currentTimeMillis() + minutes * 60_000L
    handler.postDelayed(sleepRunnable, minutes * 60_000L)
  }

  // ---- favorites / history / playlists ----

  fun toggleFavorite(id: Long) {
    favorites = if (id in favorites) favorites - id else favorites + id
    savePrefs()
    MusicWidgetUpdater.update(app)
  }

  private fun addHistory(id: Long) {
    history = (listOf(id) + history.filter { it != id }).take(100)
    savePrefs()
  }

  fun clearHistory() {
    history = emptyList()
    savePrefs()
  }

  fun createPlaylist(
    name: String,
    firstTrack: Long? = null,
  ) {
    val n = name.trim()
    if (n.isEmpty() || playlists.containsKey(n)) {
      if (firstTrack != null && playlists.containsKey(n)) addToPlaylist(n, firstTrack)
      return
    }
    playlists = playlists + (n to (if (firstTrack != null) listOf(firstTrack) else emptyList()))
    savePrefs()
  }

  fun addToPlaylist(
    name: String,
    id: Long,
  ) {
    val cur = playlists[name] ?: return
    if (id in cur) return
    playlists = playlists + (name to (cur + id))
    savePrefs()
  }

  fun removeFromPlaylist(
    name: String,
    id: Long,
  ) {
    val cur = playlists[name] ?: return
    playlists = playlists + (name to cur.filter { it != id })
    savePrefs()
  }

  fun deletePlaylist(name: String) {
    playlists = playlists - name
    savePrefs()
  }

  private fun loadPrefs() {
    val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    favorites = p.getString("fav", "")!!.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    history = p.getString("history", "")!!.split(",").mapNotNull { it.toLongOrNull() }
    val map = linkedMapOf<String, List<Long>>()
    try {
      val obj = JSONObject(p.getString("playlists", "{}")!!)
      for (k in obj.keys()) {
        val arr = obj.getJSONArray(k)
        map[k] = (0 until arr.length()).map { arr.getLong(it) }
      }
    } catch (_: Exception) {
    }
    playlists = map
  }

  private fun savePrefs() {
    val obj = JSONObject()
    playlists.forEach { (k, v) -> obj.put(k, JSONArray(v)) }
    app
      .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit()
      .putString("fav", favorites.joinToString(","))
      .putString("history", history.joinToString(","))
      .putString("playlists", obj.toString())
      .apply()
  }

  // ---- remember queue / backup ----

  /** Saves the queue, song and position so they can be brought back next time. */
  fun saveQueueState() {
    if (!initialised || queue.isEmpty() || !MusicSettings.restoreQueue) return
    app
      .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit()
      .putString("q_ids", queue.joinToString(",") { it.id.toString() })
      .putInt("q_index", index)
      .putInt("q_pos", position())
      .putBoolean("q_shuffle", shuffle)
      .putInt("q_repeat", repeat)
      .putFloat("q_speed", speed)
      .apply()
  }

  private fun clearSavedQueue() {
    if (!initialised) return
    app
      .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit()
      .remove("q_ids")
      .remove("q_index")
      .remove("q_pos")
      .apply()
  }

  /** Puts the last queue back (paused) so the mini player shows where you stopped. */
  fun restoreQueue(byId: Map<Long, MusicTrack>) {
    if (!initialised || queue.isNotEmpty() || !MusicSettings.restoreQueue) return
    val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val ids = p.getString("q_ids", "")!!.split(",").mapNotNull { it.toLongOrNull() }
    val list = ids.mapNotNull { byId[it] }
    if (list.isEmpty()) return
    val savedId = ids.getOrNull(p.getInt("q_index", 0))
    baseQueue = list
    queue.addAll(list)
    index = list.indexOfFirst { it.id == savedId }.coerceAtLeast(0)
    shuffle = p.getBoolean("q_shuffle", false)
    repeat = p.getInt("q_repeat", 0).coerceIn(0, 2)
    speed = p.getFloat("q_speed", 1f)
    restoredTrackId = list[index].id
    restoredPosMs = p.getInt("q_pos", 0)
    isPlaying = false
  }

  fun exportJson(): JSONObject {
    val pl = JSONObject()
    playlists.forEach { (k, v) -> pl.put(k, JSONArray(v)) }
    return JSONObject()
      .put("favorites", JSONArray(favorites.toList()))
      .put("history", JSONArray(history))
      .put("playlists", pl)
  }

  fun importJson(o: JSONObject) {
    o.optJSONArray("favorites")?.let { a ->
      favorites = (0 until a.length()).map { a.getLong(it) }.toSet()
    }
    o.optJSONArray("history")?.let { a ->
      history = (0 until a.length()).map { a.getLong(it) }
    }
    o.optJSONObject("playlists")?.let { obj ->
      val map = linkedMapOf<String, List<Long>>()
      for (k in obj.keys()) {
        val arr = obj.getJSONArray(k)
        map[k] = (0 until arr.length()).map { arr.getLong(it) }
      }
      playlists = map
    }
    savePrefs()
  }

  // ---- focus / release / session / notification ----

  private fun requestFocus(): Boolean {
    val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (focusRequest == null) {
      focusRequest =
        AudioFocusRequest
          .Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(
            AudioAttributes
              .Builder()
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .build(),
          ).setOnAudioFocusChangeListener { change ->
            when (change) {
              AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocus = false
                pause()
              }
              AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (MusicSettings.duck) {
                  try {
                    player?.setVolume(0.2f, 0.2f)
                  } catch (_: Exception) {
                  }
                } else {
                  resumeOnFocus = isPlaying
                  pause()
                }
              }
              AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocus = isPlaying
                pause()
              }
              AudioManager.AUDIOFOCUS_GAIN -> {
                try {
                  player?.setVolume(1f, 1f)
                } catch (_: Exception) {
                }
                if (resumeOnFocus) {
                  resumeOnFocus = false
                  resume()
                }
              }
            }
          }.build()
    }
    return am.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  private fun releasePlayer() {
    try {
      player?.release()
    } catch (_: Exception) {
    }
    player = null
    prepared = false
  }

  fun stopAll() {
    releasePlayer()
    handler.removeCallbacks(sleepRunnable)
    sleepEndAt = 0L
    queue.clear()
    baseQueue = emptyList()
    index = -1
    isPlaying = false
    clearSavedQueue()
    session?.isActive = false
    app.stopService(Intent(app, MusicPlaybackService::class.java))
    MusicWidgetUpdater.update(app)
  }

  /** Swaps an edited song into the queue and tells the screens and widgets. */
  fun applyTagEdit(t: MusicTrack) {
    for (i in queue.indices) if (queue[i].id == t.id) queue[i] = t
    baseQueue = baseQueue.map { if (it.id == t.id) t else it }
    libraryVersion++
    try {
      updateSession()
    } catch (_: Exception) {
    }
    MusicWidgetUpdater.update(app)
  }

  /** True while the widget is loading the library to start music in a fresh process. */
  @Volatile var widgetLoading = false

  /**
   * Used by the home screen widget. If a queue is loaded it just plays or pauses. If the app was closed it
   * brings back the last queue (or the library, starting at the last played song) and plays it.
   */
  fun startFromWidget(
    context: Context,
    pauseIfPlaying: Boolean = true,
  ) {
    init(context)
    if (queue.isNotEmpty()) {
      if (pauseIfPlaying) togglePlay()
      return
    }
    if (widgetLoading) return
    widgetLoading = true
    Thread {
      val tracks = try { loadMusicTracks(app) } catch (_: Exception) { emptyList() }
      handler.post {
        widgetLoading = false
        if (queue.isEmpty() && tracks.isNotEmpty()) {
          restoreQueue(tracks.associateBy { it.id })
          if (queue.isNotEmpty()) {
            resume()
          } else {
            val last = history.firstOrNull()
            play(tracks, tracks.indexOfFirst { it.id == last }.coerceAtLeast(0))
          }
        }
        if (current == null) app.stopService(Intent(app, MusicPlaybackService::class.java))
      }
    }.start()
  }

  private fun onStateChanged() {
    saveQueueState()
    updateSession()
    MusicWidgetUpdater.update(app)
    if (current == null) return
    try {
      if (serviceRunning) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(app))
      } else {
        ContextCompat.startForegroundService(app, Intent(app, MusicPlaybackService::class.java))
      }
    } catch (_: Exception) {
    }
  }

  private fun updateSession() {
    val s = session ?: return
    val t = current
    val md = MediaMetadataCompat.Builder()
    if (t != null) {
      md.putString(MediaMetadataCompat.METADATA_KEY_TITLE, t.title)
      md.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, t.artist)
      md.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, t.album)
      md.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, t.durationMs)
      if (MusicSettings.lockArt) notifArt?.let { md.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, if (MusicSettings.lockBlur) fakeBlur(it) else it) }
    }
    s.setMetadata(md.build())
    val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
    s.setPlaybackState(
      PlaybackStateCompat
        .Builder()
        .setActions(
          PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP,
        ).setState(state, position().toLong(), if (isPlaying) speed else 0f)
        .build(),
    )
  }

  fun buildNotification(ctx: Context): Notification {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      nm.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Music playback", NotificationManager.IMPORTANCE_LOW),
      )
    }
    fun action(
      icon: Int,
      title: String,
      act: String,
    ): NotificationCompat.Action {
      val pi =
        PendingIntent.getService(
          ctx,
          act.hashCode(),
          Intent(ctx, MusicPlaybackService::class.java).setAction(act),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
      return NotificationCompat.Action(icon, title, pi)
    }
    val t = current
    val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
    val content =
      if (launch != null) {
        PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
      } else {
        null
      }
    val style = androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2)
    session?.sessionToken?.let { style.setMediaSession(it) }
    return NotificationCompat
      .Builder(ctx, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(t?.title ?: "Music")
      .setContentText(t?.artist ?: "")
      .setSubText(t?.album)
      .setLargeIcon(if (MusicSettings.lockArt) (if (MusicSettings.lockBlur) notifArt?.let { fakeBlur(it) } else notifArt) else null)
      .setContentIntent(content)
      .setOnlyAlertOnce(true)
      .setOngoing(isPlaying)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(action(android.R.drawable.ic_media_previous, "Previous", MusicPlaybackService.ACTION_PREV))
      .addAction(
        action(
          if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
          "Play/Pause",
          MusicPlaybackService.ACTION_TOGGLE,
        ),
      ).addAction(action(android.R.drawable.ic_media_next, "Next", MusicPlaybackService.ACTION_NEXT))
      .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", MusicPlaybackService.ACTION_STOP))
      .setStyle(style)
      .build()
  }
}

/** Foreground service that keeps the music playing in the background and shows the notification. */
class MusicPlaybackService : Service() {
  companion object {
    const val ACTION_TOGGLE = "app.marlboroadvance.mpvex.music.TOGGLE"
    const val ACTION_NEXT = "app.marlboroadvance.mpvex.music.NEXT"
    const val ACTION_PREV = "app.marlboroadvance.mpvex.music.PREV"
    const val ACTION_STOP = "app.marlboroadvance.mpvex.music.STOP"
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    MusicController.serviceRunning = true
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    MusicController.init(applicationContext)
    when (intent?.action) {
      ACTION_TOGGLE -> if (MusicController.queue.isEmpty()) MusicController.startFromWidget(applicationContext) else MusicController.togglePlay()
      ACTION_NEXT -> if (MusicController.queue.isEmpty()) MusicController.startFromWidget(applicationContext, false) else MusicController.next()
      ACTION_PREV -> if (MusicController.queue.isEmpty()) MusicController.startFromWidget(applicationContext, false) else MusicController.previous()
      ACTION_STOP -> {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        MusicController.stopAll()
        stopSelf()
        return START_NOT_STICKY
      }
    }
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
    try {
      ServiceCompat.startForeground(this, MusicController.NOTIF_ID, MusicController.buildNotification(this), type)
    } catch (_: Exception) {
    }
    if (MusicController.current == null && !MusicController.widgetLoading) stopSelf()
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    MusicController.serviceRunning = false
    super.onDestroy()
  }
}
