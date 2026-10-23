package app.marlboroadvance.mpvex.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
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

/** The four widgets in the picker. Each one starts at its own size; after that the layout follows the real size. */
enum class MusicWidgetKind(
  val defaultWidthDp: Int,
  val defaultHeightDp: Int,
) {
  Small(70, 70),
  Medium(150, 150),
  Wide(320, 70),
  Large(320, 150),
}

/** The layout a widget uses at its current size. Every widget can be resized into any of them. */
private enum class WidgetShape { Tiny, Compact, Square, Wide, Large }

private fun shapeFor(
  wDp: Int,
  hDp: Int,
): WidgetShape =
  when {
    wDp < 100 -> WidgetShape.Tiny
    // thin widget: dark card, cover on the left, title, artist and one row of buttons
    hDp < 100 && (wDp < 270 || hDp < 62) -> WidgetShape.Compact
    hDp < 100 -> WidgetShape.Wide
    wDp < 260 -> WidgetShape.Square
    else -> WidgetShape.Large
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

class MusicWidgetWideProvider : MusicWidgetProvider()

class MusicWidgetLargeProvider : MusicWidgetProvider()

/** Handles the favourite and repeat buttons without opening the app. */
class MusicWidgetActionReceiver : BroadcastReceiver() {
  companion object {
    const val ACTION_FAVORITE = "app.marlboroadvance.mpvex.widget.FAVORITE"
    const val ACTION_REPEAT = "app.marlboroadvance.mpvex.widget.REPEAT"
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
      }
    } catch (_: Throwable) {
    }
  }
}

/**
 * Draws all music widgets from the state of [MusicController]. The play, previous, next, favourite and
 * repeat buttons act directly, so the app does not open. Tapping anywhere else opens the player.
 */
object MusicWidgetUpdater {
  private const val SNAP = "music_widget_state"
  private const val TICK_MS = 2000L

  private val executor = Executors.newSingleThreadExecutor()
  private val main = Handler(Looper.getMainLooper())

  @Volatile private var appCtx: Context? = null
  private val tick = Runnable { appCtx?.let { update(it) } }

  private var artTrackId = Long.MIN_VALUE
  private var art: Bitmap? = null
  private var blurFor: Bitmap? = null
  private var blurBmp: Bitmap? = null

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
      MusicWidgetKind.Wide to MusicWidgetWideProvider::class.java,
      MusicWidgetKind.Large to MusicWidgetLargeProvider::class.java,
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
    // keep the progress bar and time moving while a song plays
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
        .apply()
      return State(true, t.title, t.artist, MusicController.isPlaying, pos, t.durationMs, art, fav, MusicController.repeat)
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

  // ---- favourite / repeat (used by the widget buttons) ----

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

  // ---- views ----

  private fun build(
    ctx: Context,
    kind: MusicWidgetKind,
    options: Bundle,
    st: State,
  ): RemoteViews {
    val density = ctx.resources.displayMetrics.density
    val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: kind.defaultWidthDp
    val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: kind.defaultHeightDp
    val shape = shapeFor(wDp, hDp)
    val layout =
      when (shape) {
        WidgetShape.Tiny -> R.layout.widget_music_small
        WidgetShape.Compact -> R.layout.widget_music_compact
        WidgetShape.Square -> R.layout.widget_music_medium
        WidgetShape.Wide -> R.layout.widget_music_wide
        WidgetShape.Large -> R.layout.widget_music_large
      }
    val rv = RemoteViews(ctx.packageName, layout)
    val s31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    rv.setImageViewResource(R.id.widget_play_icon, if (st.playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play)

    // background bitmap, drawn at a size that keeps the widget update small
    val scale = min(1f, 480f / max(wDp, hDp) / density)
    val bgW = max(1, (wDp * density * scale).toInt())
    val bgH = max(1, (hDp * density * scale).toInt())
    val radius = 16f * density * scale
    val stroke = 1.2f * density * scale
    val cover = st.art ?: placeholderArt(ctx)
    val bg =
      when (shape) {
        WidgetShape.Tiny -> hero(cover, bgW, bgH, radius, stroke, 0xB4)
        WidgetShape.Square -> hero(cover, bgW, bgH, radius, stroke, 0xE8)
        WidgetShape.Compact -> compactCard(cover, bgW, bgH, radius, stroke)
        else -> glass(blurOf(st.art), bgW, bgH, radius, stroke)
      }
    rv.setImageViewBitmap(R.id.widget_bg, bg)

    if (shape != WidgetShape.Tiny) {
      rv.setTextViewText(R.id.widget_title, if (st.hasTrack) st.title else ctx.getString(R.string.music_widget_idle_title))
      rv.setTextViewText(R.id.widget_artist, if (st.hasTrack) st.artist else ctx.getString(R.string.music_widget_idle_artist))
    }
    val favRes = if (st.fav) R.drawable.widget_ic_fav_on else R.drawable.widget_ic_fav
    val repRes =
      when (st.repeat) {
        1 -> R.drawable.widget_ic_repeat_all
        2 -> R.drawable.widget_ic_repeat_one
        else -> R.drawable.widget_ic_repeat_off
      }

    // what fits at this size (the buttons always keep their full tappable size)
    when (shape) {
      WidgetShape.Tiny -> {
        val rowFav = wDp >= 215 // wide enough for repeat and heart in the button row
        val prevNext = wDp >= 128
        rv.setImageViewResource(R.id.widget_fav_top_icon, favRes)
        rv.setImageViewResource(R.id.widget_fav_icon, favRes)
        rv.setImageViewResource(R.id.widget_repeat_icon, repRes)
        rv.setViewVisibility(R.id.widget_brand, if (wDp >= 56 && hDp >= 56) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_fav_top, if (!rowFav && wDp >= 56) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_prev, if (prevNext) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_next, if (prevNext) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_repeat, if (rowFav) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_fav, if (rowFav) View.VISIBLE else View.GONE)
        setProgress(rv, st)
      }
      WidgetShape.Compact -> {
        // only show what really fits, so nothing can overlap the buttons
        val textW = wDp - hDp - 16
        val fiveButtons = textW >= 150
        val prevNext = textW >= 90
        rv.setImageViewResource(R.id.widget_fav_icon, favRes)
        rv.setImageViewResource(R.id.widget_repeat_icon, repRes)
        rv.setViewVisibility(R.id.widget_title, if (hDp >= 52) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_artist, if (hDp >= 66) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_progress, if (hDp >= 72) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_prev, if (prevNext) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_next, if (prevNext) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_repeat, if (fiveButtons) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_fav, if (fiveButtons) View.VISIBLE else View.GONE)
        setProgress(rv, st)
        // text starts after the cover, which is as wide as the widget is high
        rv.setViewPadding(
          R.id.widget_compact_content,
          ((hDp + 8) * density).toInt(),
          (3 * density).toInt(),
          (8 * density).toInt(),
          (3 * density).toInt(),
        )
      }
      WidgetShape.Square -> {
        rv.setImageViewResource(R.id.widget_fav_top_icon, favRes)
        rv.setImageViewResource(R.id.widget_repeat_icon, repRes)
        rv.setViewVisibility(R.id.widget_artist, if (hDp >= 125) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_progress_row, if (hDp >= 110) View.VISIBLE else View.GONE)
        setTimes(rv, st, wDp >= 140)
      }
      WidgetShape.Wide -> {
        val showArt = wDp >= 380
        val colW = wDp - 14 - (if (showArt) 66 else 0) - 196
        rv.setImageViewResource(R.id.widget_fav_icon, favRes)
        rv.setImageViewResource(R.id.widget_repeat_icon, repRes)
        rv.setViewVisibility(R.id.widget_art, if (showArt) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.widget_artist, if (hDp >= 62) View.VISIBLE else View.GONE)
        if (showArt) rv.setImageViewBitmap(R.id.widget_art, thumb(cover, (56 * density).toInt()))
        setTimes(rv, st, colW >= 110)
      }
      WidgetShape.Large -> {
        val artDp = min((hDp - 16).coerceIn(56, 150), (wDp * 0.36f).toInt()).coerceAtLeast(48)
        val colW = wDp - 18 - artDp - 12
        rv.setImageViewResource(R.id.widget_fav_icon, favRes)
        rv.setImageViewResource(R.id.widget_repeat_icon, repRes)
        if (s31) {
          rv.setViewLayoutWidth(R.id.widget_art, artDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
          rv.setViewLayoutHeight(R.id.widget_art, artDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        val shownDp = if (s31) artDp else 84
        rv.setImageViewBitmap(R.id.widget_art, thumb(cover, (shownDp * density).toInt()))
        setTimes(rv, st, colW >= 150)
      }
    }

    // buttons: control the music, the app stays closed
    rv.setOnClickPendingIntent(R.id.widget_play, service(ctx, MusicPlaybackService.ACTION_TOGGLE, 101))
    rv.setOnClickPendingIntent(R.id.widget_prev, service(ctx, MusicPlaybackService.ACTION_PREV, 102))
    rv.setOnClickPendingIntent(R.id.widget_next, service(ctx, MusicPlaybackService.ACTION_NEXT, 103))
    val fav = broadcast(ctx, MusicWidgetActionReceiver.ACTION_FAVORITE, 104)
    rv.setOnClickPendingIntent(R.id.widget_repeat, broadcast(ctx, MusicWidgetActionReceiver.ACTION_REPEAT, 105))
    when (shape) {
      WidgetShape.Tiny -> {
        rv.setOnClickPendingIntent(R.id.widget_fav_top, fav)
        rv.setOnClickPendingIntent(R.id.widget_fav, fav)
      }
      WidgetShape.Square -> rv.setOnClickPendingIntent(R.id.widget_fav_top, fav)
      else -> rv.setOnClickPendingIntent(R.id.widget_fav, fav)
    }

    // everything else (background, cover, title) opens the player on the Now Playing screen
    val open = openApp(ctx)
    rv.setOnClickPendingIntent(R.id.widget_root, open)
    if (shape != WidgetShape.Tiny) rv.setOnClickPendingIntent(R.id.widget_title_area, open)
    if (shape == WidgetShape.Wide || shape == WidgetShape.Large) rv.setOnClickPendingIntent(R.id.widget_art, open)
    return rv
  }

  private fun setProgress(
    rv: RemoteViews,
    st: State,
  ) {
    val ratio = if (st.durMs > 0) (st.posMs.toFloat() / st.durMs).coerceIn(0f, 1f) else 0f
    rv.setProgressBar(R.id.widget_progress, 1000, (ratio * 1000).toInt(), false)
  }

  private fun setTimes(
    rv: RemoteViews,
    st: State,
    show: Boolean,
  ) {
    setProgress(rv, st)
    rv.setTextViewText(R.id.widget_elapsed, fmt(st.posMs))
    rv.setTextViewText(R.id.widget_total, fmt(st.durMs))
    rv.setViewVisibility(R.id.widget_elapsed, if (show) View.VISIBLE else View.GONE)
    rv.setViewVisibility(R.id.widget_total, if (show) View.VISIBLE else View.GONE)
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

  private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
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

  private fun rounded(
    src: Bitmap,
    radiusFraction: Float,
  ): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val r = min(src.width, src.height) * radiusFraction
    Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, p)
    return out
  }

  /** The small cover with rounded corners shown on the wide and large widgets. */
  private fun thumb(
    cover: Bitmap,
    px: Int,
  ): Bitmap {
    val size = px.coerceIn(64, 320)
    return rounded(Bitmap.createScaledBitmap(soft(cover), size, size, true), 0.2f)
  }

  private fun placeholderArt(ctx: Context): Bitmap {
    val size = 128
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFF6C63FF.toInt(), 0xFF2A2547.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    ContextCompat.getDrawable(ctx, R.drawable.widget_ic_note)?.let {
      val pad = size / 4
      it.setBounds(pad, pad, size - pad, size - pad)
      it.draw(c)
    }
    return out
  }

  private fun border(
    c: Canvas,
    rect: RectF,
    radius: Float,
    stroke: Float,
  ) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.style = Paint.Style.STROKE
    p.strokeWidth = stroke
    p.color = 0x44FFFFFF
    val half = stroke / 2f
    c.drawRoundRect(RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half), radius, radius, p)
  }

  /** Flat dark card with the cover flush on the left (square, as high as the card), for thin widgets. */
  private fun compactCard(
    cover: Bitmap,
    w: Int,
    h: Int,
    radius: Float,
    stroke: Float,
  ): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF232326.toInt()
    c.drawRoundRect(rect, radius, radius, p)
    val side = min(w, h)
    p.shader = BitmapShader(centerCrop(cover, side, side), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    // rounded on the card's left corners, straight where it meets the dark part
    c.drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), radius, radius, p)
    c.drawRect(RectF(side / 2f, 0f, side.toFloat(), side.toFloat()), p)
    border(c, rect, radius, stroke)
    return out
  }

  /** Full-bleed cover with a dark fade towards the bottom, for the 1x1 and 2x2 widgets. */
  private fun hero(
    cover: Bitmap,
    w: Int,
    h: Int,
    radius: Float,
    stroke: Float,
    bottomAlpha: Int,
  ): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = BitmapShader(centerCrop(cover, w, h), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    c.drawRoundRect(rect, radius, radius, p)
    p.shader =
      LinearGradient(
        0f,
        0f,
        0f,
        h.toFloat(),
        intArrayOf(0x00101030, 0x22101030, (bottomAlpha shl 24) or 0x0A0D26),
        floatArrayOf(0f, 0.35f, 1f),
        Shader.TileMode.CLAMP,
      )
    c.drawRoundRect(rect, radius, radius, p)
    border(c, rect, radius, stroke)
    return out
  }

  /** Blurred cover under a blue-violet gradient, for the wide and large widgets. */
  private fun glass(
    blur: Bitmap?,
    w: Int,
    h: Int,
    radius: Float,
    stroke: Float,
  ): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val navy = 0xFF0B0D2A.toInt()
    val indigo = 0xFF22308F.toInt()
    if (blur != null) {
      p.shader = BitmapShader(centerCrop(blur, w, h), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      c.drawRoundRect(rect, radius, radius, p)
      val avg = Bitmap.createScaledBitmap(blur, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()
      val tint = ColorUtils.blendARGB(avg, indigo, 0.35f)
      p.shader =
        LinearGradient(
          0f,
          0f,
          w.toFloat(),
          h.toFloat(),
          (tint and 0x00FFFFFF) or (0xB0 shl 24),
          (navy and 0x00FFFFFF) or (0xEE shl 24),
          Shader.TileMode.CLAMP,
        )
    } else {
      p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), indigo, navy, Shader.TileMode.CLAMP)
    }
    c.drawRoundRect(rect, radius, radius, p)
    border(c, rect, radius, stroke)
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
