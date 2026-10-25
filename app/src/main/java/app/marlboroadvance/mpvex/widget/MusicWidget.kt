package app.marlboroadvance.mpvex.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import app.marlboroadvance.mpvex.MainActivity
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.browser.music.MusicController
import app.marlboroadvance.mpvex.ui.browser.music.MusicPlaybackService
import app.marlboroadvance.mpvex.ui.browser.music.MusicSettings
import app.marlboroadvance.mpvex.ui.browser.music.loadArt
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** The four widgets in the picker. Each starts at its own size and can be resized freely within its limits. */
enum class MusicWidgetKind(
  val defaultWidthDp: Int,
  val defaultHeightDp: Int,
) {
  Small(72, 72),
  Medium(144, 72),
  Large(288, 72),
  Text(144, 72),
}

abstract class MusicWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    val pending = goAsync()
    MusicWidgetUpdater.update(context) { pending.finish() }
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
  ) {
    val pending = goAsync()
    MusicWidgetUpdater.update(context) { pending.finish() }
  }
}

class MusicWidgetSmallProvider : MusicWidgetProvider()

class MusicWidgetMediumProvider : MusicWidgetProvider()

class MusicWidgetLargeProvider : MusicWidgetProvider()

class MusicWidgetTextProvider : MusicWidgetProvider()

/** Handles the favourite, repeat and shuffle buttons without opening the app. */
class MusicWidgetActionReceiver : BroadcastReceiver() {
  companion object {
    const val ACTION_FAVORITE = "app.marlboroadvance.mpvex.widget.FAVORITE"
    const val ACTION_REPEAT = "app.marlboroadvance.mpvex.widget.REPEAT"
    const val ACTION_SHUFFLE = "app.marlboroadvance.mpvex.widget.SHUFFLE"
  }

  override fun onReceive(
    context: Context,
    intent: Intent,
  ) {
    val ctx = context.applicationContext
    try {
      MusicController.init(ctx)
      when (intent.action) {
        ACTION_FAVORITE -> MusicWidgetUpdater.toggleFavorite(ctx)
        ACTION_REPEAT -> MusicWidgetUpdater.cycleRepeat(ctx)
        ACTION_SHUFFLE -> MusicWidgetUpdater.toggleShuffle(ctx)
      }
    } catch (_: Throwable) {
    }
  }
}

/**
 * Draws all music widgets from the state of [MusicController]. Every button acts directly through the
 * playback service or the receiver above, so the app stays closed. Tapping anywhere else opens the player.
 */
object MusicWidgetUpdater {
  private const val SNAP = "music_widget_state"
  private const val TICK_MS = 3000L
  private const val TOUCH_DP = 38

  private val executor = Executors.newSingleThreadExecutor()
  private val main = Handler(Looper.getMainLooper())

  @Volatile private var appCtx: Context? = null
  private val tick = Runnable { appCtx?.let { update(it) } }

  private var artTrackId = Long.MIN_VALUE
  private var art: Bitmap? = null
  private var blurFor: Bitmap? = null
  private var blurBmp: Bitmap? = null
  private var placeholder: Bitmap? = null

  private class State(
    val hasTrack: Boolean,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val posMs: Long,
    val durMs: Long,
    val art: Bitmap?,
    val fav: Boolean = false,
    val repeat: Int = 0,
    val shuffle: Boolean = false,
  )

  fun update(
    context: Context,
    done: (() -> Unit)? = null,
  ) {
    val ctx = context.applicationContext
    appCtx = ctx
    executor.execute {
      try {
        render(ctx)
      } catch (_: Throwable) {
      } finally {
        done?.invoke()
      }
    }
  }

  private fun providers(): List<Pair<MusicWidgetKind, Class<*>>> =
    listOf(
      MusicWidgetKind.Small to MusicWidgetSmallProvider::class.java,
      MusicWidgetKind.Medium to MusicWidgetMediumProvider::class.java,
      MusicWidgetKind.Large to MusicWidgetLargeProvider::class.java,
      MusicWidgetKind.Text to MusicWidgetTextProvider::class.java,
    )

  private fun render(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val all = mutableListOf<Pair<MusicWidgetKind, Int>>()
    for ((kind, cls) in providers()) {
      for (id in mgr.getAppWidgetIds(ComponentName(ctx, cls))) all.add(kind to id)
    }
    main.removeCallbacks(tick)
    if (all.isEmpty()) return
    MusicSettings.init(ctx)
    val st = readState(ctx)
    for ((kind, id) in all) {
      try {
        mgr.updateAppWidget(id, build(ctx, kind, mgr.getAppWidgetOptions(id), st))
      } catch (_: Throwable) {
      }
    }
    // keep the progress line moving while a song plays
    if (st.playing) main.postDelayed(tick, TICK_MS)
  }

  // ---- state ----

  private fun readState(ctx: Context): State {
    val prefs = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
    val t = MusicController.current
    if (t != null) {
      if (artTrackId != t.id) {
        artTrackId = t.id
        art = try { loadArt(ctx, t, 384)?.let { squareCrop(it, 320) } } catch (_: Throwable) { null }
        saveArt(ctx, art)
      }
      val pos = try { MusicController.position().toLong() } catch (_: Throwable) { 0L }
      val fav = t.id in MusicController.favorites
      prefs
        .edit()
        .putString("title", t.title)
        .putString("artist", t.artist)
        .putLong("dur", t.durationMs)
        .putLong("pos", pos)
        .putLong("id", t.id)
        .putBoolean("fav", fav)
        .putInt("repeat", MusicController.repeat)
        .putBoolean("shuffle", MusicController.shuffle)
        .apply()
      return State(true, t.title, t.artist, MusicController.isPlaying, pos, t.durationMs, art, fav, MusicController.repeat, MusicController.shuffle)
    }
    // Nothing loaded in this process (app was closed): show the last song, paused
    val title = prefs.getString("title", null)
    if (title == null) return State(false, "", "", false, 0, 0, null)
    if (art == null && artTrackId == Long.MIN_VALUE) {
      art = loadSavedArt(ctx)
      artTrackId = Long.MIN_VALUE + 1
    }
    return State(
      true,
      title,
      prefs.getString("artist", "") ?: "",
      false,
      prefs.getLong("pos", 0L),
      prefs.getLong("dur", 0L),
      art,
      prefs.getBoolean("fav", false),
      prefs.getInt("repeat", 0),
      prefs.getBoolean("shuffle", false),
    )
  }

  private fun artFile(ctx: Context) = File(ctx.cacheDir, "music_widget_art.png")

  private fun saveArt(
    ctx: Context,
    b: Bitmap?,
  ) {
    try {
      val f = artFile(ctx)
      if (b == null) {
        f.delete()
      } else {
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) }
      }
    } catch (_: Throwable) {
    }
  }

  private fun loadSavedArt(ctx: Context): Bitmap? =
    try {
      val f = artFile(ctx)
      if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    } catch (_: Throwable) {
      null
    }

  // ---- favourite / repeat / shuffle (used by the widget buttons) ----

  fun toggleFavorite(ctx: Context) {
    val prefs = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
    val id = MusicController.current?.id ?: prefs.getLong("id", -1L)
    if (id < 0) return
    // write the new state first so a widget redraw never shows the old heart
    prefs.edit().putBoolean("fav", id !in MusicController.favorites).apply()
    MusicController.toggleFavorite(id)
  }

  fun cycleRepeat(ctx: Context) {
    val prefs = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
    val cur = if (MusicController.queue.isNotEmpty()) MusicController.repeat else prefs.getInt("repeat", 0)
    val next = (cur + 1) % 3
    prefs.edit().putInt("repeat", next).apply()
    MusicController.setRepeatMode(next)
  }

  fun toggleShuffle(ctx: Context) {
    val prefs = ctx.getSharedPreferences(SNAP, Context.MODE_PRIVATE)
    val cur = if (MusicController.queue.isNotEmpty()) MusicController.shuffle else prefs.getBoolean("shuffle", false)
    prefs.edit().putBoolean("shuffle", !cur).apply()
    MusicController.setShuffleMode(!cur)
    update(ctx)
  }

  // ---- views ----

  private class Look(
    val title: Int,
    val sub: Int,
    val icon: Int,
    val dim: Int,
    val active: Int,
  )

  private enum class Btn { Shuffle, Prev, Play, Next, Repeat, Fav }

  private enum class Mode { Small, Tall, Row, Text }

  private fun build(
    ctx: Context,
    kind: MusicWidgetKind,
    options: Bundle,
    st: State,
  ): RemoteViews {
    val density = ctx.resources.displayMetrics.density
    val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val wDp =
      options
        .getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        .takeIf { it > 0 } ?: kind.defaultWidthDp
    val hDp =
      options
        .getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        .takeIf { it > 0 } ?: kind.defaultHeightDp

    // the layout follows the shape: tiny -> cover and buttons, wide -> cover on the left, tall -> cover on top
    val mode =
      when {
        kind == MusicWidgetKind.Text -> Mode.Text
        wDp < 100 && hDp < 100 -> Mode.Small
        hDp * 1.15f > wDp -> Mode.Tall
        else -> Mode.Row
      }
    val layout =
      when (mode) {
        Mode.Small -> R.layout.widget_music_small
        Mode.Tall -> R.layout.widget_music_tall
        Mode.Row -> R.layout.widget_music_row
        Mode.Text -> R.layout.widget_music_text
      }
    val rv = RemoteViews(ctx.packageName, layout)
    val g = Geo(density, wDp, hDp, min(1f, 480f / max(wDp, hDp) / density))
    val cover = st.art ?: placeholder ?: placeholderArt(ctx).also { placeholder = it }
    val blur = blurOf(cover)
    val avg = averageOf(blur)
    val title = if (st.hasTrack) st.title else ctx.getString(R.string.music_widget_idle_title)
    val artist = if (st.hasTrack) st.artist else ctx.getString(R.string.music_widget_idle_artist)
    val ratio = if (st.durMs > 0) (st.posMs.toFloat() / st.durMs).coerceIn(0f, 1f) else 0f

    val look = Look(0xFFFFFFFF.toInt(), 0xFFB9C4E8.toInt(), 0xFFFFFFFF.toInt(), 0x80FFFFFF.toInt(), ACCENT)
    val showTitle: Boolean
    val showArtist: Boolean
    val showProgress: Boolean
    val avail: Int
    val heartCorner: Boolean
    var priority: List<Btn> = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Repeat, Btn.Shuffle)
    when (mode) {
      Mode.Small -> {
        showTitle = false
        showArtist = false
        showProgress = false
        avail = wDp - 8
        heartCorner = true
        rv.setImageViewBitmap(R.id.w_art, roundedArt(cover))
      }
      Mode.Tall -> {
        showTitle = hDp >= 118
        showArtist = hDp >= 148
        showProgress = hDp >= 132
        avail = wDp - 16
        heartCorner = wDp >= 100
        rv.setImageViewBitmap(R.id.w_art, roundedArt(cover))
      }
      Mode.Row -> {
        showTitle = hDp >= 50
        showArtist = hDp >= 84
        showProgress = hDp >= 100 && wDp >= 200
        avail = wDp - (hDp - 12) - 6 - 8 - 6 - 4
        heartCorner = kind != MusicWidgetKind.Large
        rv.setImageViewBitmap(R.id.w_art, roundedArt(cover))
      }
      Mode.Text -> {
        showTitle = true
        showArtist = hDp >= 88
        showProgress = hDp >= 108
        avail = wDp - 16
        heartCorner = false
        rv.setViewVisibility(R.id.w_note, vis(hDp >= 96))
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Fav, Btn.Shuffle, Btn.Repeat)
      }
    }
    if (mode != Mode.Text && !heartCorner) priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Repeat, Btn.Fav, Btn.Shuffle)
    if (mode != Mode.Small && mode != Mode.Text && heartCorner) priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Repeat, Btn.Shuffle)
    rv.setImageViewBitmap(R.id.w_bg, glassBg(g, blur, avg, if (showProgress) -1f else ratio))

    text(rv, title, artist, look, showTitle, showArtist)
    if (mode != Mode.Small) {
      rv.setViewVisibility(R.id.w_progress_row, vis(showProgress))
      rv.setProgressBar(R.id.w_progress, 1000, (ratio * 1000).toInt(), false)
      rv.setTextViewText(R.id.w_elapsed, fmt(st.posMs))
      rv.setTextViewText(R.id.w_total, fmt(st.durMs))
    }
    rv.setViewVisibility(R.id.w_ov_fav, vis(heartCorner))
    if (heartCorner) {
      rv.setImageViewResource(R.id.w_ov_fav_icon, favRes(st.fav))
      rv.setInt(R.id.w_ov_fav_icon, "setColorFilter", if (st.fav) FAV_ON else 0xFFFFFFFF.toInt())
      rv.setOnClickPendingIntent(R.id.w_ov_fav, broadcast(ctx, MusicWidgetActionReceiver.ACTION_FAVORITE, 104))
    }
    buttons(ctx, rv, st, look, if (mode == Mode.Small) listOf(Btn.Play, Btn.Prev, Btn.Next) else priority, avail)

    // everything else (background, cover, title) opens the player on the Now Playing screen
    val open = openApp(ctx)
    rv.setOnClickPendingIntent(R.id.w_root, open)
    rv.setOnClickPendingIntent(R.id.w_title_area, open)
    rv.setOnClickPendingIntent(R.id.w_art, open)
    return rv
  }

  private class Geo(
    val density: Float,
    val wDp: Int,
    val hDp: Int,
    val scale: Float,
  ) {
    val bw = max(1, (wDp * density * scale).toInt())
    val bh = max(1, (hDp * density * scale).toInt())

    /** dp -> pixels of the background bitmap */
    fun b(dp: Float) = dp * density * scale
  }

  private val FAV_ON = 0xFFFF5C8A.toInt()
  private val ACCENT = 0xFF8AB4FF.toInt()
  private val DARK = 0xFF14142B.toInt()

  private fun vis(v: Boolean) = if (v) View.VISIBLE else View.GONE

  private fun favRes(on: Boolean) = if (on) R.drawable.wg_fav_on else R.drawable.wg_fav

  private fun repeatRes(mode: Int) =
    when (mode) {
      1 -> R.drawable.wg_repeat_all
      2 -> R.drawable.wg_repeat_one
      else -> R.drawable.wg_repeat_off
    }

  private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
  }

  private fun text(
    rv: RemoteViews,
    title: String,
    artist: String,
    look: Look,
    showTitle: Boolean,
    showArtist: Boolean,
  ) {
    rv.setTextViewText(R.id.w_title, title)
    rv.setTextViewText(R.id.w_artist, artist)
    rv.setTextColor(R.id.w_title, look.title)
    rv.setTextColor(R.id.w_artist, look.sub)
    rv.setViewVisibility(R.id.w_title_area, vis(showTitle))
    rv.setViewVisibility(R.id.w_artist, vis(showArtist))
  }

  /**
   * Shows as many buttons as fit, most important first, and wires them all.
   * Previous, play and next are always there, whatever the size.
   */
  private fun buttons(
    ctx: Context,
    rv: RemoteViews,
    st: State,
    look: Look,
    priority: List<Btn>,
    availDp: Int,
  ) {
    val n = ((availDp + 4) / TOUCH_DP).coerceIn(min(3, priority.size), priority.size)
    val shown = priority.take(n).toSet()
    fun id(b: Btn) =
      when (b) {
        Btn.Shuffle -> R.id.w_shuffle
        Btn.Prev -> R.id.w_prev
        Btn.Play -> R.id.w_play
        Btn.Next -> R.id.w_next
        Btn.Repeat -> R.id.w_repeat
        Btn.Fav -> R.id.w_fav
      }
    fun icon(b: Btn) =
      when (b) {
        Btn.Shuffle -> R.id.w_shuffle_icon
        Btn.Prev -> R.id.w_prev_icon
        Btn.Play -> R.id.w_play_icon
        Btn.Next -> R.id.w_next_icon
        Btn.Repeat -> R.id.w_repeat_icon
        Btn.Fav -> R.id.w_fav_icon
      }
    for (b in Btn.values()) rv.setViewVisibility(id(b), vis(b in shown))
    rv.setImageViewResource(R.id.w_play_icon, if (st.playing) R.drawable.wg_pause else R.drawable.wg_play)
    rv.setImageViewResource(R.id.w_prev_icon, R.drawable.wg_prev)
    rv.setImageViewResource(R.id.w_next_icon, R.drawable.wg_next)
    rv.setImageViewResource(R.id.w_repeat_icon, repeatRes(st.repeat))
    rv.setImageViewResource(R.id.w_fav_icon, favRes(st.fav))
    rv.setInt(icon(Btn.Play), "setColorFilter", DARK)
    rv.setInt(icon(Btn.Prev), "setColorFilter", look.icon)
    rv.setInt(icon(Btn.Next), "setColorFilter", look.icon)
    rv.setInt(icon(Btn.Shuffle), "setColorFilter", if (st.shuffle) look.active else look.dim)
    rv.setInt(icon(Btn.Repeat), "setColorFilter", if (st.repeat != 0) look.active else look.dim)
    rv.setInt(icon(Btn.Fav), "setColorFilter", if (st.fav) FAV_ON else look.icon)
    rv.setOnClickPendingIntent(R.id.w_play, service(ctx, MusicPlaybackService.ACTION_TOGGLE, 101))
    rv.setOnClickPendingIntent(R.id.w_prev, service(ctx, MusicPlaybackService.ACTION_PREV, 102))
    rv.setOnClickPendingIntent(R.id.w_next, service(ctx, MusicPlaybackService.ACTION_NEXT, 103))
    rv.setOnClickPendingIntent(R.id.w_fav, broadcast(ctx, MusicWidgetActionReceiver.ACTION_FAVORITE, 104))
    rv.setOnClickPendingIntent(R.id.w_repeat, broadcast(ctx, MusicWidgetActionReceiver.ACTION_REPEAT, 105))
    rv.setOnClickPendingIntent(R.id.w_shuffle, broadcast(ctx, MusicWidgetActionReceiver.ACTION_SHUFFLE, 106))
  }

  private fun service(
    ctx: Context,
    action: String,
    code: Int,
  ): PendingIntent =
    PendingIntent.getForegroundService(
      ctx,
      code,
      Intent(ctx, MusicPlaybackService::class.java).setAction(action),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun broadcast(
    ctx: Context,
    action: String,
    code: Int,
  ): PendingIntent =
    PendingIntent.getBroadcast(
      ctx,
      code,
      Intent(ctx, MusicWidgetActionReceiver::class.java).setAction(action),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun openApp(ctx: Context): PendingIntent =
    PendingIntent.getActivity(
      ctx,
      201,
      Intent(ctx, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_MUSIC)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  // ---- artwork is its own view, so it always sits next to the text whatever size the launcher reports ----

  private fun roundedArt(cover: Bitmap): Bitmap {
    val s = 256
    val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    p.shader = BitmapShader(centerCrop(cover, s, s), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    Canvas(out).drawRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), s * 0.16f, s * 0.16f, p)
    return out
  }

  /** Blurred cover under a dark blue glass tint, rounded, with a thin play line along the bottom when there is no progress bar. */
  private fun glassBg(
    g: Geo,
    blur: Bitmap?,
    avg: Int,
    lineRatio: Float,
  ): Bitmap {
    val bmp = Bitmap.createBitmap(g.bw, g.bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val rect = RectF(0f, 0f, g.bw.toFloat(), g.bh.toFloat())
    if (blur != null) c.drawBitmap(centerCrop(blur, g.bw, g.bh), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    val tint = Paint()
    tint.color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(avg, 0xFF0B1230.toInt(), 0.72f), 0xDC)
    c.drawRect(rect, tint)
    if (lineRatio >= 0f) {
      val l = g.b(14f)
      val r = g.bw - l
      val h = max(2f, g.b(3f))
      val bottom = g.bh - g.b(4f)
      val p = Paint(Paint.ANTI_ALIAS_FLAG)
      p.color = 0x44FFFFFF
      c.drawRoundRect(RectF(l, bottom - h, r, bottom), h / 2, h / 2, p)
      p.color = 0xFFFFFFFF.toInt()
      if (lineRatio > 0f) c.drawRoundRect(RectF(l, bottom - h, l + (r - l) * lineRatio, bottom), h / 2, h / 2, p)
    }
    val rad = min(g.b(16f), min(g.bw, g.bh) / 2f)
    val mask = Bitmap.createBitmap(g.bw, g.bh, Bitmap.Config.ARGB_8888)
    Canvas(mask).drawRoundRect(rect, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG))
    val mp = Paint()
    mp.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    c.drawBitmap(mask, 0f, 0f, mp)
    val sw = max(1f, g.b(1f))
    val bp = Paint(Paint.ANTI_ALIAS_FLAG)
    bp.style = Paint.Style.STROKE
    bp.strokeWidth = sw
    bp.color = 0x40FFFFFF
    c.drawRoundRect(RectF(sw / 2, sw / 2, g.bw - sw / 2, g.bh - sw / 2), rad, rad, bp)
    return bmp
  }

  private fun averageOf(blur: Bitmap?): Int =
    try {
      if (blur == null) 0xFF6C63FF.toInt() else Bitmap.createScaledBitmap(blur, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()
    } catch (_: Throwable) {
      0xFF6C63FF.toInt()
    }

  // ---- bitmaps ----

  /** Thumbnails can be hardware bitmaps, which cannot be cropped or drawn into a widget bitmap. */
  private fun soft(src: Bitmap): Bitmap = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src

  private fun squareCrop(
    src: Bitmap,
    size: Int,
  ): Bitmap {
    val s = soft(src)
    val side = min(s.width, s.height)
    val x = (s.width - side) / 2
    val y = (s.height - side) / 2
    return Bitmap.createScaledBitmap(Bitmap.createBitmap(s, x, y, side, side), size, size, true)
  }

  /** Scales [src] to fill w x h, cutting off whatever does not fit (like centerCrop). */
  private fun centerCrop(
    src: Bitmap,
    w: Int,
    h: Int,
  ): Bitmap {
    val s = soft(src)
    val srcRatio = s.width.toFloat() / s.height
    val dstRatio = w.toFloat() / h
    val cw: Float
    val ch: Float
    if (srcRatio > dstRatio) {
      ch = s.height.toFloat()
      cw = ch * dstRatio
    } else {
      cw = s.width.toFloat()
      ch = cw / dstRatio
    }
    val l = (s.width - cw) / 2f
    val t = (s.height - ch) / 2f
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(
      s,
      Rect(l.toInt(), t.toInt(), (l + cw).toInt().coerceAtMost(s.width), (t + ch).toInt().coerceAtMost(s.height)),
      RectF(0f, 0f, w.toFloat(), h.toFloat()),
      Paint(Paint.FILTER_BITMAP_FLAG),
    )
    return out
  }

  private fun placeholderArt(ctx: Context): Bitmap {
    val size = 128
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = android.graphics.LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFF9B6CFF.toInt(), 0xFF5B3CE0.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    ContextCompat.getDrawable(ctx, R.drawable.wg_note)?.let {
      val pad = size / 4
      it.setBounds(pad, pad, size - pad, size - pad)
      it.draw(c)
    }
    return out
  }

  // ---- blur ----

  private fun blurOf(a: Bitmap?): Bitmap? {
    if (a == null) return null
    if (blurFor !== a) {
      blurFor = a
      blurBmp = try { blurred(a) } catch (_: Throwable) { null }
    }
    return blurBmp
  }

  /** A tiny, heavily blurred copy; stretched up to the widget size it looks like a soft colour wash. */
  private fun blurred(src: Bitmap): Bitmap {
    val size = 32
    val small = Bitmap.createScaledBitmap(soft(src), size, size, true)
    val px = IntArray(size * size)
    small.getPixels(px, 0, size, 0, 0, size, size)
    repeat(3) { boxBlur(px, size, size, 3) }
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, size, 0, 0, size, size)
    return out
  }

  private fun boxBlur(
    px: IntArray,
    w: Int,
    h: Int,
    r: Int,
  ) {
    val tmp = IntArray(px.size)
    blurPass(px, tmp, w, h, r, true)
    blurPass(tmp, px, w, h, r, false)
  }

  private fun blurPass(
    src: IntArray,
    dst: IntArray,
    w: Int,
    h: Int,
    r: Int,
    horizontal: Boolean,
  ) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    val div = 2 * r + 1
    for (line in 0 until lines) {
      var a = 0
      var rr = 0
      var g = 0
      var b = 0
      for (k in -r..r) {
        val c = src[at(line, k.coerceIn(0, len - 1), w, horizontal)]
        a += c ushr 24
        rr += (c shr 16) and 255
        g += (c shr 8) and 255
        b += c and 255
      }
      for (i in 0 until len) {
        dst[at(line, i, w, horizontal)] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
        val add = src[at(line, min(i + r + 1, len - 1), w, horizontal)]
        val sub = src[at(line, max(i - r, 0), w, horizontal)]
        a += (add ushr 24) - (sub ushr 24)
        rr += ((add shr 16) and 255) - ((sub shr 16) and 255)
        g += ((add shr 8) and 255) - ((sub shr 8) and 255)
        b += (add and 255) - (sub and 255)
      }
    }
  }

  private fun at(
    line: Int,
    i: Int,
    w: Int,
    horizontal: Boolean,
  ): Int = if (horizontal) line * w + i else i * w + line
}
