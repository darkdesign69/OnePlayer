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

/** The five widgets in the picker. Each starts at its own size and can be resized freely afterwards. */
enum class MusicWidgetKind(
  val defaultWidthDp: Int,
  val defaultHeightDp: Int,
) {
  Capsule(280, 70),
  Square(150, 150),
  Mini(180, 70),
  Album(280, 120),
  Expanded(280, 150),
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

class MusicWidgetCapsuleProvider : MusicWidgetProvider()

class MusicWidgetSquareProvider : MusicWidgetProvider()

class MusicWidgetMiniProvider : MusicWidgetProvider()

class MusicWidgetAlbumProvider : MusicWidgetProvider()

class MusicWidgetExpandedProvider : MusicWidgetProvider()

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
  private const val TICK_MS = 2000L
  private const val TOUCH_DP = 44

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
      MusicWidgetKind.Capsule to MusicWidgetCapsuleProvider::class.java,
      MusicWidgetKind.Square to MusicWidgetSquareProvider::class.java,
      MusicWidgetKind.Mini to MusicWidgetMiniProvider::class.java,
      MusicWidgetKind.Album to MusicWidgetAlbumProvider::class.java,
      MusicWidgetKind.Expanded to MusicWidgetExpandedProvider::class.java,
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
    // keep the progress bar moving while a song plays (only the expanded widget shows it)
    if (st.playing && all.any { it.first == MusicWidgetKind.Expanded }) main.postDelayed(tick, TICK_MS)
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
    val layout =
      when (kind) {
        MusicWidgetKind.Capsule -> R.layout.widget_music_capsule
        MusicWidgetKind.Square -> R.layout.widget_music_square
        MusicWidgetKind.Mini -> R.layout.widget_music_mini
        MusicWidgetKind.Album -> R.layout.widget_music_album
        MusicWidgetKind.Expanded -> R.layout.widget_music_expanded
      }
    val rv = RemoteViews(ctx.packageName, layout)
    val g = Geo(density, wDp, hDp, min(1f, 480f / max(wDp, hDp) / density))
    val cover = st.art ?: placeholder ?: placeholderArt(ctx).also { placeholder = it }
    val blur = blurOf(cover)
    val avg = averageOf(blur)
    val title = if (st.hasTrack) st.title else ctx.getString(R.string.music_widget_idle_title)
    val artist = if (st.hasTrack) st.artist else ctx.getString(R.string.music_widget_idle_artist)

    val look: Look
    val priority: List<Btn>
    val avail: Int
    var showProgress = true
    when (kind) {
      MusicWidgetKind.Capsule -> {
        // the cover is its own view (a circle as high as the pill), so the text can never run over it
        avail = wDp - (hDp - 14) - 2 - 12
        rv.setImageViewBitmap(R.id.w_bg, capsuleBg(g, blur, avg))
        rv.setImageViewBitmap(R.id.w_art, artOf(cover, 256, 0.5f))
        pad(rv, g, 0, 2, 0, 2)
        look = Look(0xFFFFFFFF.toInt(), 0xFFBFC0C8.toInt(), 0xFFFFFFFF.toInt(), 0x99FFFFFF.toInt(), KIT_ACCENT)
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Shuffle, Btn.Repeat, Btn.Fav)
        text(rv, if (artist.isNotBlank() && st.hasTrack) "$title - $artist" else title, null, look, 13f, 11f, true, false)
      }
      MusicWidgetKind.Square -> {
        avail = wDp - 16
        rv.setImageViewBitmap(R.id.w_bg, squareBg(g, blur, avg))
        rv.setImageViewBitmap(R.id.w_art, artOf(cover, 300, 0.1f))
        pad(rv, g, 8, 4, 8, 6)
        val dark = 0xFF1D1630.toInt()
        look = Look(0xFF1B1430.toInt(), 0xFF33254A.toInt(), dark, ColorUtils.setAlphaComponent(dark, 0x88), 0xFF7A3FE0.toInt())
        val overlays = wDp >= 110 && hDp >= 110
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next) + if (overlays) emptyList() else listOf(Btn.Repeat, Btn.Fav, Btn.Shuffle)
        text(rv, title, artist, look, (hDp / 9f).coerceIn(13f, 17f), 11f, true, hDp >= 140)
        rv.setViewVisibility(R.id.w_ov_repeat, vis(overlays))
        rv.setViewVisibility(R.id.w_ov_fav, vis(overlays))
        if (overlays) {
          overlay(ctx, rv, R.id.w_ov_repeat, R.id.w_ov_repeat_icon, repeatRes(st.repeat), if (st.repeat != 0) KIT_ACCENT else 0xFFFFFFFF.toInt(), MusicWidgetActionReceiver.ACTION_REPEAT, 105)
          overlay(ctx, rv, R.id.w_ov_fav, R.id.w_ov_fav_icon, favRes(st.fav), if (st.fav) FAV_ON else 0xFFFFFFFF.toInt(), MusicWidgetActionReceiver.ACTION_FAVORITE, 104)
        }
      }
      MusicWidgetKind.Mini -> {
        avail = wDp - 16
        rv.setImageViewBitmap(R.id.w_bg, miniBg(g, blur, avg))
        pad(rv, g, 8, 4, 8, 4)
        look = Look(0xFFEAF8F8.toInt(), 0xFFA9D0D1.toInt(), 0xFFFFFFFF.toInt(), 0x99FFFFFF.toInt(), KIT_ACCENT)
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Fav, Btn.Repeat, Btn.Shuffle)
        text(rv, title, artist, look, 14f, 12f, true, hDp >= 100)
      }
      MusicWidgetKind.Album, MusicWidgetKind.Expanded -> {
        val expanded = kind == MusicWidgetKind.Expanded
        val inset = 10
        val side = if (expanded) min(hDp - inset * 2, (wDp * 0.4f).toInt()) else hDp - inset * 2
        val showArt = wDp >= 150 && side >= 50
        val padStart = if (showArt) inset + side + 12 else 12
        avail = wDp - padStart - 10
        rv.setImageViewBitmap(R.id.w_bg, albumBg(g, blur, avg, expanded))
        rv.setViewVisibility(R.id.w_art, vis(showArt))
        if (showArt) rv.setImageViewBitmap(R.id.w_art, artOf(cover, 300, 0.16f))
        pad(rv, g, if (showArt) 0 else 12, 4, 0, 4)
        showProgress = hDp >= 84
        look =
          Look(
            0xFFFFFFFF.toInt(),
            if (expanded) 0xFFC5CCD8.toInt() else 0xFF9CB9F2.toInt(),
            0xFFFFFFFF.toInt(),
            0x99FFFFFF.toInt(),
            KIT_ACCENT,
          )
        val showOv = wDp >= 150 && hDp >= 70
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Repeat, Btn.Shuffle) + if (showOv) emptyList() else listOf(Btn.Fav)
        val showTitle = true
        val showArtist = hDp >= (if (expanded) 100 else 96)
        text(rv, title, artist, look, (hDp / 7f).coerceIn(14f, 20f), (hDp / 9f).coerceIn(11f, 15f), showTitle, showArtist)
        // keep the text clear of the heart in the corner
        rv.setViewPadding(R.id.w_title_area, 0, 0, if (showOv) g.px(22) else 0, 0)
        rv.setViewVisibility(R.id.w_ov_fav, vis(showOv))
        if (showOv) {
          overlay(ctx, rv, R.id.w_ov_fav, R.id.w_ov_fav_icon, favRes(st.fav), if (st.fav) FAV_ON else 0xFFFFFFFF.toInt(), MusicWidgetActionReceiver.ACTION_FAVORITE, 104)
        }
      }
    }
    // progress bar in every widget
    val ratio = if (st.durMs > 0) (st.posMs.toFloat() / st.durMs).coerceIn(0f, 1f) else 0f
    rv.setViewVisibility(R.id.w_progress, vis(showProgress))
    rv.setProgressBar(R.id.w_progress, 1000, (ratio * 1000).toInt(), false)
    buttons(ctx, rv, st, look, priority, avail, rewind = kind == MusicWidgetKind.Album || kind == MusicWidgetKind.Expanded)

    // everything else (background, cover, title) opens the player on the Now Playing screen
    val open = openApp(ctx)
    rv.setOnClickPendingIntent(R.id.w_root, open)
    rv.setOnClickPendingIntent(R.id.w_title_area, open)
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

    fun px(dp: Int) = (dp * density).toInt()
  }

  private val FAV_ON = 0xFFFF5C8A.toInt()
  private val KIT_ACCENT = 0xFFFFB38B.toInt()

  private fun vis(v: Boolean) = if (v) View.VISIBLE else View.GONE

  private fun pad(
    rv: RemoteViews,
    g: Geo,
    l: Int,
    t: Int,
    r: Int,
    b: Int,
  ) = rv.setViewPadding(R.id.w_content, g.px(l), g.px(t), g.px(r), g.px(b))

  private fun favRes(on: Boolean) = if (on) R.drawable.wg_fav_on else R.drawable.wg_fav

  private fun repeatRes(mode: Int) =
    when (mode) {
      1 -> R.drawable.wg_repeat_all
      2 -> R.drawable.wg_repeat_one
      else -> R.drawable.wg_repeat_off
    }

  private fun text(
    rv: RemoteViews,
    title: String,
    subtitle: String?,
    look: Look,
    titleDp: Float,
    artistDp: Float,
    showTitle: Boolean,
    showArtist: Boolean,
  ) {
    rv.setTextViewText(R.id.w_title, title)
    rv.setTextViewText(R.id.w_artist, subtitle ?: "")
    rv.setTextColor(R.id.w_title, look.title)
    rv.setTextColor(R.id.w_artist, look.sub)
    rv.setTextViewTextSize(R.id.w_title, TypedValue.COMPLEX_UNIT_DIP, titleDp)
    rv.setTextViewTextSize(R.id.w_artist, TypedValue.COMPLEX_UNIT_DIP, artistDp)
    rv.setViewVisibility(R.id.w_title_area, vis(showTitle))
    rv.setViewVisibility(R.id.w_artist, vis(showArtist && subtitle != null))
  }

  /** Shows as many buttons as fit at full touch size, most important first, and wires them all. */
  private fun buttons(
    ctx: Context,
    rv: RemoteViews,
    st: State,
    look: Look,
    priority: List<Btn>,
    availDp: Int,
    rewind: Boolean = false,
  ) {
    val n = ((availDp + 8) / TOUCH_DP).coerceIn(min(3, priority.size), priority.size)
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
    rv.setImageViewResource(R.id.w_prev_icon, if (rewind) R.drawable.wg_rew else R.drawable.wg_prev)
    rv.setImageViewResource(R.id.w_next_icon, if (rewind) R.drawable.wg_ffw else R.drawable.wg_next)
    rv.setImageViewResource(R.id.w_repeat_icon, repeatRes(st.repeat))
    rv.setImageViewResource(R.id.w_fav_icon, favRes(st.fav))
    rv.setInt(icon(Btn.Play), "setColorFilter", look.icon)
    rv.setInt(icon(Btn.Prev), "setColorFilter", look.icon)
    rv.setInt(icon(Btn.Next), "setColorFilter", look.icon)
    rv.setInt(icon(Btn.Shuffle), "setColorFilter", if (st.shuffle) look.active else look.dim)
    rv.setInt(icon(Btn.Repeat), "setColorFilter", if (st.repeat != 0) look.active else look.dim)
    rv.setInt(icon(Btn.Fav), "setColorFilter", if (st.fav) FAV_ON else look.dim)
    rv.setOnClickPendingIntent(R.id.w_play, service(ctx, MusicPlaybackService.ACTION_TOGGLE, 101))
    rv.setOnClickPendingIntent(R.id.w_prev, service(ctx, MusicPlaybackService.ACTION_PREV, 102))
    rv.setOnClickPendingIntent(R.id.w_next, service(ctx, MusicPlaybackService.ACTION_NEXT, 103))
    rv.setOnClickPendingIntent(R.id.w_fav, broadcast(ctx, MusicWidgetActionReceiver.ACTION_FAVORITE, 104))
    rv.setOnClickPendingIntent(R.id.w_repeat, broadcast(ctx, MusicWidgetActionReceiver.ACTION_REPEAT, 105))
    rv.setOnClickPendingIntent(R.id.w_shuffle, broadcast(ctx, MusicWidgetActionReceiver.ACTION_SHUFFLE, 106))
  }

  private fun overlay(
    ctx: Context,
    rv: RemoteViews,
    box: Int,
    icon: Int,
    res: Int,
    tint: Int,
    action: String,
    code: Int,
  ) {
    rv.setImageViewResource(icon, res)
    rv.setInt(icon, "setColorFilter", tint)
    rv.setOnClickPendingIntent(box, broadcast(ctx, action, code))
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

  // ---- backgrounds: each widget is one bitmap, drawn at a size that keeps the update small ----

  private fun canvasFor(
    g: Geo,
    radiusDp: Float,
  ): Triple<Bitmap, Canvas, RectF> {
    val bmp = Bitmap.createBitmap(g.bw, g.bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    return Triple(bmp, c, RectF(0f, 0f, g.bw.toFloat(), g.bh.toFloat()))
  }

  /** Cuts the bitmap to a rounded shape with smooth edges. */
  private fun round(
    bmp: Bitmap,
    g: Geo,
    radiusDp: Float,
  ) {
    val r = min(g.b(radiusDp), min(g.bw, g.bh) / 2f)
    val mask = Bitmap.createBitmap(g.bw, g.bh, Bitmap.Config.ARGB_8888)
    Canvas(mask).drawRoundRect(RectF(0f, 0f, g.bw.toFloat(), g.bh.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG))
    val p = Paint()
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    Canvas(bmp).drawBitmap(mask, 0f, 0f, p)
  }

  /** Blurred cover stretched over [rect], covered by a flat tint. */
  private fun wash(
    c: Canvas,
    blur: Bitmap?,
    rect: RectF,
    tint: Int,
    alpha: Int,
  ) {
    val w = max(1, rect.width().toInt())
    val h = max(1, rect.height().toInt())
    if (blur != null) c.drawBitmap(centerCrop(blur, w, h), rect.left, rect.top, Paint(Paint.FILTER_BITMAP_FLAG))
    val p = Paint()
    p.color = ColorUtils.setAlphaComponent(tint, alpha)
    c.drawRect(rect, p)
  }

  private fun frame(
    c: Canvas,
    g: Geo,
    radiusDp: Float,
  ) {
    val sw = max(1f, g.b(1f))
    val r = min(g.b(radiusDp), min(g.bw, g.bh) / 2f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.style = Paint.Style.STROKE
    p.strokeWidth = sw
    p.color = 0x2AFFFFFF
    c.drawRoundRect(RectF(sw / 2, sw / 2, g.bw - sw / 2, g.bh - sw / 2), r, r, p)
  }

  private fun capsuleBg(
    g: Geo,
    blur: Bitmap?,
    avg: Int,
  ): Bitmap {
    val (bmp, c, rect) = canvasFor(g, g.hDp / 2f)
    wash(c, blur, rect, ColorUtils.blendARGB(avg, 0xFF202228.toInt(), 0.8f), 0xE0)
    round(bmp, g, g.hDp / 2f)
    frame(c, g, g.hDp / 2f)
    return bmp
  }

  private fun squareBg(
    g: Geo,
    blur: Bitmap?,
    avg: Int,
  ): Bitmap {
    val (bmp, c, rect) = canvasFor(g, 26f)
    wash(c, blur, rect, ColorUtils.blendARGB(avg, Color.WHITE, 0.74f), 0xE8)
    round(bmp, g, 26f)
    frame(c, g, 26f)
    return bmp
  }

  private fun miniBg(
    g: Geo,
    blur: Bitmap?,
    avg: Int,
  ): Bitmap {
    val (bmp, c, rect) = canvasFor(g, 20f)
    wash(c, blur, rect, ColorUtils.blendARGB(avg, 0xFF073F43.toInt(), 0.62f), 0xD0)
    round(bmp, g, 20f)
    frame(c, g, 20f)
    return bmp
  }

  private fun albumBg(
    g: Geo,
    blur: Bitmap?,
    avg: Int,
    expanded: Boolean,
  ): Bitmap {
    val (bmp, c, rect) = canvasFor(g, 24f)
    val base = if (expanded) 0xFF394354.toInt() else 0xFF24365F.toInt()
    wash(c, blur, rect, ColorUtils.blendARGB(avg, base, if (expanded) 0.66f else 0.7f), 0xE2)
    round(bmp, g, 24f)
    frame(c, g, 24f)
    return bmp
  }

  /** The cover as a square bitmap with rounded corners ([frac] 0.5 = a circle). */
  private fun artOf(
    cover: Bitmap,
    size: Int,
    frac: Float,
  ): Bitmap {
    val sq = squareCrop(cover, size)
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    p.shader = BitmapShader(sq, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val r = size * frac
    Canvas(out).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, p)
    return out
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
