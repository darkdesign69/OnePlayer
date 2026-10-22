package app.marlboroadvance.mpvex.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
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

/** The four widget sizes. They differ in layout and in the sizes they can be resized to. */
enum class MusicWidgetKind(
  val defaultWidthDp: Int,
  val defaultHeightDp: Int,
) {
  Small(110, 110),
  Medium(144, 68),
  Large(288, 68),
  Text(144, 68),
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

/**
 * Draws all music widgets from the state of [MusicController]. The play, previous and next buttons talk
 * to the music service directly, so the app does not open. Only the cover and the title open the app.
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

  private class State(
    val hasTrack: Boolean,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val posMs: Long,
    val durMs: Long,
    val art: Bitmap?,
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
        art = try { loadArt(ctx, t, 192)?.let { squareCrop(it, 160) } } catch (_: Throwable) { null }
        saveArt(ctx, art)
      }
      val pos = try { MusicController.position().toLong() } catch (_: Throwable) { 0L }
      prefs
        .edit()
        .putString("title", t.title)
        .putString("artist", t.artist)
        .putLong("dur", t.durationMs)
        .putLong("pos", pos)
        .apply()
      return State(true, t.title, t.artist, MusicController.isPlaying, pos, t.durationMs, art)
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
    val tall = hDp >= 100 && (kind == MusicWidgetKind.Medium || kind == MusicWidgetKind.Large)
    val layout =
      when (kind) {
        MusicWidgetKind.Small -> R.layout.widget_music_small
        MusicWidgetKind.Text -> R.layout.widget_music_text
        else -> if (tall) R.layout.widget_music_wide_tall else R.layout.widget_music_wide
      }
    val rv = RemoteViews(ctx.packageName, layout)

    rv.setTextViewText(R.id.widget_title, if (st.hasTrack) st.title else ctx.getString(R.string.music_widget_idle_title))
    rv.setTextViewText(R.id.widget_artist, if (st.hasTrack) st.artist else ctx.getString(R.string.music_widget_idle_artist))
    rv.setImageViewResource(R.id.widget_play_icon, if (st.playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play)

    // background: blurred, dark cover with rounded corners
    val scale = min(1f, 384f / max(wDp, hDp) / density)
    val bgW = max(1, (wDp * density * scale).toInt())
    val bgH = max(1, (hDp * density * scale).toInt())
    rv.setImageViewBitmap(R.id.widget_bg, background(st.art, bgW, bgH, 16f * density * scale))

    // cover
    if (kind != MusicWidgetKind.Text) {
      rv.setImageViewBitmap(R.id.widget_art, rounded(st.art ?: placeholderArt(ctx), 0.2f))
    }

    // what fits at this size
    if (kind == MusicWidgetKind.Medium || kind == MusicWidgetKind.Large) {
      val artSize = if (tall) 72 else 48
      val padH = if (tall) 20 else 12
      val showArt = wDp >= 120
      val column = wDp - padH - (if (showArt) artSize + 8 else 0)
      rv.setViewVisibility(R.id.widget_art, if (showArt) View.VISIBLE else View.GONE)
      val showTimes = column >= 150
      rv.setViewVisibility(R.id.widget_elapsed, if (showTimes) View.VISIBLE else View.GONE)
      rv.setViewVisibility(R.id.widget_total, if (showTimes) View.VISIBLE else View.GONE)
      rv.setViewVisibility(R.id.widget_brand, if (column >= 90) View.VISIBLE else View.GONE)
      val ratio = if (st.durMs > 0) (st.posMs.toFloat() / st.durMs).coerceIn(0f, 1f) else 0f
      rv.setProgressBar(R.id.widget_progress, 1000, (ratio * 1000).toInt(), false)
      rv.setTextViewText(R.id.widget_elapsed, fmt(st.posMs))
      rv.setTextViewText(R.id.widget_total, fmt(st.durMs))
    }

    if (kind == MusicWidgetKind.Small) {
      // the small widget can be resized: show only what fits
      rv.setViewVisibility(R.id.widget_art, if (hDp >= 100) View.VISIBLE else View.GONE)
      rv.setViewVisibility(R.id.widget_artist, if (hDp >= 130) View.VISIBLE else View.GONE)
    }
    if ((kind == MusicWidgetKind.Medium || kind == MusicWidgetKind.Large) && !tall) {
      // one row high: only the title and the buttons, so nothing is squeezed
      val roomy = hDp >= 90
      rv.setViewVisibility(R.id.widget_progress, if (roomy) View.VISIBLE else View.GONE)
      rv.setViewVisibility(R.id.widget_artist, if (roomy) View.VISIBLE else View.GONE)
    }
    if (kind == MusicWidgetKind.Text) {
      rv.setViewVisibility(R.id.widget_artist, if (hDp >= 80) View.VISIBLE else View.GONE)
    }
    if (tall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // taller widget: the cover grows with the height so the widget never looks empty
      val side = (hDp - 28).coerceIn(56, 140).toFloat()
      rv.setViewLayoutWidth(R.id.widget_art, side, TypedValue.COMPLEX_UNIT_DIP)
      rv.setViewLayoutHeight(R.id.widget_art, side, TypedValue.COMPLEX_UNIT_DIP)
    }

    // buttons: control the music service, the app stays closed
    rv.setOnClickPendingIntent(R.id.widget_play, service(ctx, MusicPlaybackService.ACTION_TOGGLE, 101))
    rv.setOnClickPendingIntent(R.id.widget_prev, service(ctx, MusicPlaybackService.ACTION_PREV, 102))
    rv.setOnClickPendingIntent(R.id.widget_next, service(ctx, MusicPlaybackService.ACTION_NEXT, 103))

    // only the cover and the title/artist open the app (on the Now Playing screen)
    val open = openApp(ctx)
    rv.setOnClickPendingIntent(R.id.widget_title_area, open)
    if (kind != MusicWidgetKind.Text) rv.setOnClickPendingIntent(R.id.widget_art, open)
    return rv
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

  private fun squareCrop(
    src: Bitmap,
    size: Int,
  ): Bitmap {
    // thumbnails can be hardware bitmaps, which cannot be cropped or drawn into a widget bitmap
    val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
    val side = min(soft.width, soft.height)
    val x = (soft.width - side) / 2
    val y = (soft.height - side) / 2
    return Bitmap.createScaledBitmap(Bitmap.createBitmap(soft, x, y, side, side), size, size, true)
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

  private fun background(
    art: Bitmap?,
    w: Int,
    h: Int,
    radius: Float,
  ): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (art != null) {
      // tiny copy stretched back up looks blurred
      val tiny = Bitmap.createScaledBitmap(art, 12, 12, true)
      val big = Bitmap.createScaledBitmap(tiny, w, h, true)
      p.shader = BitmapShader(big, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      c.drawRoundRect(rect, radius, radius, p)
      p.shader = null
      p.color = 0xB3101018.toInt()
      c.drawRoundRect(rect, radius, radius, p)
    } else {
      p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xF01E1E2E.toInt(), 0xF02A2547.toInt(), Shader.TileMode.CLAMP)
      c.drawRoundRect(rect, radius, radius, p)
    }
    return out
  }
}
