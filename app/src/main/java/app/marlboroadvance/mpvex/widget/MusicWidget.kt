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
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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

/** The five widgets in the picker. Each one starts at its own size and can be resized in every direction. */
enum class MusicWidgetKind(
  val defaultWidthDp: Int,
  val defaultHeightDp: Int,
) {
  Capsule(320, 68),
  Square(150, 150),
  Mini(150, 72),
  Album(320, 150),
  Expanded(320, 150),
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
 * Draws all music widgets from the state of [MusicController]. Play, previous, next, favourite, repeat and
 * shuffle act directly, so the app does not open. Tapping anywhere else opens the player.
 */
object MusicWidgetUpdater {
  private const val SNAP = "music_widget_state"
  private const val TICK_MS = 2000L
  private const val PINK = 0xFFFF5C8A.toInt()
  private const val WHITE = 0xFFFFFFFF.toInt()

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
    val shuffle: Boolean = false,
  )

  /** Everything a widget needs to draw itself at its current size. */
  private class Env(
    val ctx: Context,
    val st: State,
    val wDp: Int,
    val hDp: Int,
    val density: Float,
    val blur: Bitmap?,
    val base: Int,
  ) {
    /** Bitmaps are drawn at a reduced size to keep the widget update small. */
    val scale: Float = min(1f, 480f / max(wDp, hDp) / density)
    val k: Float = density * scale
    val bgW: Int = max(1, (wDp * k).toInt())
    val bgH: Int = max(1, (hDp * k).toInt())
    val stroke: Float = 1.2f * k
    val s31: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  }

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
    val blur = blurOf(st.art)
    val base = baseOf(blur)
    val density = ctx.resources.displayMetrics.density
    for ((kind, id) in all) {
      try {
        val o = mgr.getAppWidgetOptions(id)
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: kind.defaultWidthDp
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: kind.defaultHeightDp
        val e = Env(ctx, st, wDp, hDp, density, blur, base)
        val rv =
          when (kind) {
            MusicWidgetKind.Capsule -> capsule(e)
            MusicWidgetKind.Square -> square(e)
            MusicWidgetKind.Mini -> mini(e)
            MusicWidgetKind.Album -> album(e)
            MusicWidgetKind.Expanded -> expanded(e)
          }
        mgr.updateAppWidget(id, rv)
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

  // ---- buttons that work without opening the app ----

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
  }

  // ---- shared view helpers ----

  private fun titleOf(st: State, ctx: Context): String = if (st.hasTrack) st.title else ctx.getString(R.string.music_widget_idle_title)

  private fun artistOf(st: State, ctx: Context): String = if (st.hasTrack) st.artist else ctx.getString(R.string.music_widget_idle_artist)

  private fun tint(
    rv: RemoteViews,
    id: Int,
    color: Int,
  ) = rv.setInt(id, "setColorFilter", color)

  private fun show(
    rv: RemoteViews,
    id: Int,
    visible: Boolean,
  ) = rv.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)

  /** Previous / play-pause / next glyphs. [rew] swaps skip icons for the rewind / forward style. */
  private fun transport(
    rv: RemoteViews,
    st: State,
    fg: Int,
    rew: Boolean,
  ) {
    rv.setImageViewResource(R.id.mw_prev_icon, if (rew) R.drawable.mw_rew else R.drawable.mw_prev)
    rv.setImageViewResource(R.id.mw_next_icon, if (rew) R.drawable.mw_ffw else R.drawable.mw_next)
    rv.setImageViewResource(R.id.mw_play_icon, if (st.playing) R.drawable.mw_pause else R.drawable.mw_play)
    tint(rv, R.id.mw_prev_icon, fg)
    tint(rv, R.id.mw_play_icon, fg)
    tint(rv, R.id.mw_next_icon, fg)
  }

  private fun extraIcons(
    rv: RemoteViews,
    st: State,
    fg: Int,
    dim: Int,
    accent: Int,
    shuffle: Boolean,
    repeat: Boolean,
    fav: Boolean,
  ) {
    if (shuffle) {
      rv.setImageViewResource(R.id.mw_shuffle_icon, R.drawable.mw_shuffle)
      tint(rv, R.id.mw_shuffle_icon, if (st.shuffle) accent else dim)
    }
    if (repeat) {
      rv.setImageViewResource(R.id.mw_repeat_icon, if (st.repeat == 2) R.drawable.mw_repeat_one else R.drawable.mw_repeat)
      tint(rv, R.id.mw_repeat_icon, if (st.repeat == 0) dim else accent)
    }
    if (fav) {
      rv.setImageViewResource(R.id.mw_fav_icon, if (st.fav) R.drawable.mw_heart_on else R.drawable.mw_heart)
      tint(rv, R.id.mw_fav_icon, if (st.fav) PINK else fg)
    }
  }

  /** Buttons control the music; everything else (the root view) opens the player's Now Playing screen. */
  private fun clicks(
    rv: RemoteViews,
    ctx: Context,
    shuffle: Boolean,
    repeat: Boolean,
    fav: Boolean,
  ) {
    rv.setOnClickPendingIntent(R.id.mw_play, service(ctx, MusicPlaybackService.ACTION_TOGGLE, 101))
    rv.setOnClickPendingIntent(R.id.mw_prev, service(ctx, MusicPlaybackService.ACTION_PREV, 102))
    rv.setOnClickPendingIntent(R.id.mw_next, service(ctx, MusicPlaybackService.ACTION_NEXT, 103))
    if (fav) rv.setOnClickPendingIntent(R.id.mw_fav, broadcast(ctx, MusicWidgetActionReceiver.ACTION_FAVORITE, 104))
    if (repeat) rv.setOnClickPendingIntent(R.id.mw_repeat, broadcast(ctx, MusicWidgetActionReceiver.ACTION_REPEAT, 105))
    if (shuffle) rv.setOnClickPendingIntent(R.id.mw_shuffle, broadcast(ctx, MusicWidgetActionReceiver.ACTION_SHUFFLE, 106))
    rv.setOnClickPendingIntent(R.id.mw_root, openApp(ctx))
  }

  private fun setArtSize(
    rv: RemoteViews,
    e: Env,
    dp: Int,
  ) {
    if (e.s31) {
      rv.setViewLayoutWidth(R.id.mw_art, dp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
      rv.setViewLayoutHeight(R.id.mw_art, dp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
    }
  }

  // ---- 1. Slim Capsule ----

  private fun capsule(e: Env): RemoteViews {
    val rv = RemoteViews(e.ctx.packageName, R.layout.widget_music_capsule)
    val showArt = e.wDp >= 200
    val artDp = if (e.s31) (e.hDp - 14).coerceIn(36, 72) else 52
    val showTitle = e.hDp >= 62
    val colW = e.wDp - 20 - (if (showArt) artDp + 12 else 0)
    val extras = colW >= 190
    val fill = ColorUtils.blendARGB(0xFF232326.toInt(), tone(e.base, 0.3f, 0.8f, 0.4f), 0.25f)
    val dim = ColorUtils.blendARGB(WHITE, fill, 0.55f)
    val accent = tone(e.base, 0.4f, 0.8f, 1f)

    rv.setImageViewBitmap(R.id.mw_bg, card(e, e.bgH / 2f, fill, null, 0))
    val t = titleOf(e.st, e.ctx)
    val a = artistOf(e.st, e.ctx)
    rv.setTextViewText(R.id.mw_title, if (e.st.hasTrack && a.isNotBlank()) "$t - $a" else t)
    show(rv, R.id.mw_title, showTitle)
    show(rv, R.id.mw_art, showArt)
    if (showArt) {
      setArtSize(rv, e, artDp)
      rv.setImageViewBitmap(R.id.mw_art, circle(e.st.art ?: placeholderArt(e.ctx), (artDp * e.density).toInt()))
    }
    show(rv, R.id.mw_shuffle, extras)
    show(rv, R.id.mw_repeat, extras)
    transport(rv, e.st, WHITE, false)
    extraIcons(rv, e.st, WHITE, dim, accent, shuffle = true, repeat = true, fav = false)
    clicks(rv, e.ctx, shuffle = true, repeat = true, fav = false)
    return rv
  }

  // ---- 2. Square Cover ----

  private fun square(e: Env): RemoteViews {
    val rv = RemoteViews(e.ctx.packageName, R.layout.widget_music_square)
    val showTitle = e.hDp >= 100
    val showArtist = e.hDp >= 128
    val panelDp = 54 + (if (showTitle) 20 else 0) + (if (showArtist) 16 else 0)
    val overlays = e.hDp - panelDp >= 50
    val light = tone(e.base, 0.10f, 0.30f, 0.97f)
    val dark = tone(e.base, 0.5f, 0.9f, 0.20f)

    val scrims = if (overlays) listOf(Triple(22f * e.k, 22f * e.k, 17f * e.k), Triple(e.bgW - 22f * e.k, 22f * e.k, 17f * e.k)) else emptyList()
    rv.setImageViewBitmap(R.id.mw_bg, squareCard(e, (panelDp * e.k).toInt(), light, scrims))
    rv.setTextViewText(R.id.mw_title, titleOf(e.st, e.ctx))
    rv.setTextViewText(R.id.mw_artist, artistOf(e.st, e.ctx))
    rv.setTextColor(R.id.mw_title, dark)
    rv.setTextColor(R.id.mw_artist, ColorUtils.blendARGB(dark, light, 0.25f))
    show(rv, R.id.mw_title_area, showTitle)
    show(rv, R.id.mw_artist, showArtist)
    show(rv, R.id.mw_repeat, overlays)
    show(rv, R.id.mw_fav, overlays)
    transport(rv, e.st, dark, false)
    extraIcons(rv, e.st, WHITE, 0xFFC9C9D6.toInt(), 0xFFE4DAFF.toInt(), shuffle = false, repeat = true, fav = true)
    clicks(rv, e.ctx, shuffle = false, repeat = true, fav = true)
    return rv
  }

  // ---- 3. Mini Controller ----

  private fun mini(e: Env): RemoteViews {
    val rv = RemoteViews(e.ctx.packageName, R.layout.widget_music_mini)
    val showTitle = e.hDp >= 64
    val showArtist = e.hDp >= 100
    val extras = e.wDp >= 230
    val dark = tone(e.base, 0.45f, 0.9f, 0.16f)
    val dim = ColorUtils.blendARGB(WHITE, dark, 0.5f)

    rv.setImageViewBitmap(R.id.mw_bg, card(e, 22f * e.k, dark, e.blur, 125))
    rv.setTextViewText(R.id.mw_title, titleOf(e.st, e.ctx))
    rv.setTextViewText(R.id.mw_artist, artistOf(e.st, e.ctx))
    show(rv, R.id.mw_title_area, showTitle)
    show(rv, R.id.mw_artist, showArtist)
    show(rv, R.id.mw_repeat, extras)
    show(rv, R.id.mw_fav, extras)
    rv.setProgressBar(R.id.mw_line, 1000, progressOf(e.st), false)
    transport(rv, e.st, WHITE, false)
    extraIcons(rv, e.st, WHITE, dim, tone(e.base, 0.35f, 0.7f, 1f), shuffle = false, repeat = true, fav = true)
    clicks(rv, e.ctx, shuffle = false, repeat = true, fav = true)
    return rv
  }

  // ---- 4. Horizontal Album ----

  private fun album(e: Env): RemoteViews {
    val rv = RemoteViews(e.ctx.packageName, R.layout.widget_music_album)
    val showArt = e.wDp >= 200
    val artDp = if (e.s31) min((e.hDp - 24).coerceIn(48, 170), (e.wDp * 0.42f).toInt()).coerceAtLeast(40) else 96
    val showTitle = e.hDp >= 70
    val showArtist = e.hDp >= 92
    val overlay = e.hDp >= 128
    val bgc = tone(e.base, 0.45f, 0.85f, 0.24f)
    val soft = tone(e.base, 0.08f, 0.2f, 0.97f)
    val artistC = tone(e.base, 0.35f, 0.65f, 0.88f)

    rv.setImageViewBitmap(R.id.mw_bg, card(e, 22f * e.k, bgc, e.blur, 70))
    rv.setTextViewText(R.id.mw_title, titleOf(e.st, e.ctx))
    rv.setTextViewText(R.id.mw_artist, artistOf(e.st, e.ctx))
    rv.setTextColor(R.id.mw_artist, artistC)
    show(rv, R.id.mw_title_area, showTitle)
    show(rv, R.id.mw_artist, showArtist)
    show(rv, R.id.mw_art, showArt)
    if (showArt) {
      setArtSize(rv, e, artDp)
      rv.setImageViewBitmap(R.id.mw_art, thumb(e.st.art ?: placeholderArt(e.ctx), (artDp * e.density).toInt()))
    }
    show(rv, R.id.mw_overlay, overlay)
    rv.setViewPadding(R.id.mw_col, 0, if (overlay) (28 * e.density).toInt() else 0, 0, 0)
    transport(rv, e.st, soft, true)
    extraIcons(rv, e.st, soft, ColorUtils.blendARGB(soft, bgc, 0.5f), artistC, shuffle = false, repeat = true, fav = true)
    clicks(rv, e.ctx, shuffle = false, repeat = true, fav = true)
    return rv
  }

  // ---- 5. Expanded Progress ----

  private fun expanded(e: Env): RemoteViews {
    val rv = RemoteViews(e.ctx.packageName, R.layout.widget_music_expanded)
    val showArt = e.wDp >= 200
    val artDp = if (e.s31) min((e.hDp * 0.62f).toInt(), (e.wDp * 0.38f).toInt()).coerceIn(48, 150) else 100
    var avail = e.hDp - 20 - 48
    val showTitle = avail >= 22
    if (showTitle) avail -= 22
    val showProgress = avail >= 18
    if (showProgress) avail -= 18
    val showArtist = showTitle && avail >= 18
    val colW = e.wDp - 24 - (if (showArt) artDp + 14 else 0)
    val overlay = e.hDp >= 110 && colW >= 160
    val times = colW >= 150
    val bgc = tone(e.base, 0.15f, 0.35f, 0.30f)
    val soft = tone(e.base, 0.06f, 0.15f, 0.97f)
    val mute = tone(e.base, 0.10f, 0.20f, 0.80f)

    rv.setImageViewBitmap(R.id.mw_bg, card(e, 22f * e.k, bgc, e.blur, 60))
    rv.setTextViewText(R.id.mw_title, titleOf(e.st, e.ctx))
    rv.setTextViewText(R.id.mw_artist, artistOf(e.st, e.ctx))
    rv.setTextColor(R.id.mw_artist, mute)
    show(rv, R.id.mw_title_area, showTitle)
    show(rv, R.id.mw_artist, showArtist)
    rv.setViewPadding(R.id.mw_title_area, 0, 0, if (overlay) (76 * e.density).toInt() else 0, 0)
    show(rv, R.id.mw_art, showArt)
    if (showArt) {
      setArtSize(rv, e, artDp)
      rv.setImageViewBitmap(R.id.mw_art, thumb(e.st.art ?: placeholderArt(e.ctx), (artDp * e.density).toInt()))
    }
    show(rv, R.id.mw_progress_row, showProgress)
    show(rv, R.id.mw_elapsed, times)
    show(rv, R.id.mw_total, times)
    rv.setTextViewText(R.id.mw_elapsed, fmt(e.st.posMs))
    rv.setTextViewText(R.id.mw_total, fmt(e.st.durMs))
    rv.setTextColor(R.id.mw_elapsed, mute)
    rv.setTextColor(R.id.mw_total, mute)
    val barDp = colW - (if (times) 60 else 0) - 8
    rv.setImageViewBitmap(R.id.mw_bar, progressBar(e, max(40, barDp)))
    show(rv, R.id.mw_overlay, overlay)
    transport(rv, e.st, soft, true)
    extraIcons(rv, e.st, soft, ColorUtils.blendARGB(soft, bgc, 0.5f), 0xFFFFA586.toInt(), shuffle = false, repeat = true, fav = true)
    clicks(rv, e.ctx, shuffle = false, repeat = true, fav = true)
    return rv
  }

  // ---- small helpers ----

  private fun progressOf(st: State): Int {
    val ratio = if (st.durMs > 0) (st.posMs.toFloat() / st.durMs).coerceIn(0f, 1f) else 0f
    return (ratio * 1000).toInt()
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

  // ---- colours ----

  /** Keeps the hue of [base] but sets saturation (within the range) and brightness; greys stay grey. */
  private fun tone(
    base: Int,
    sMin: Float,
    sMax: Float,
    v: Float,
  ): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(base, hsv)
    hsv[1] = if (hsv[1] < 0.08f) 0f else hsv[1].coerceIn(sMin, sMax)
    hsv[2] = v
    return Color.HSVToColor(hsv)
  }

  /** The main colour of the cover (average of the blurred copy), purple when there is no cover. */
  private fun baseOf(blur: Bitmap?): Int =
    if (blur == null) 0xFF7C5CE6.toInt() else Bitmap.createScaledBitmap(blur, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()

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

  /** Square cover with rounded corners (Album and Expanded widgets). */
  private fun thumb(
    cover: Bitmap,
    px: Int,
  ): Bitmap {
    val size = px.coerceIn(64, 320)
    return rounded(Bitmap.createScaledBitmap(soft(cover), size, size, true), 0.2f)
  }

  /** Round cover (Capsule widget). */
  private fun circle(
    cover: Bitmap,
    px: Int,
  ): Bitmap {
    val size = px.coerceIn(64, 320)
    val src = Bitmap.createScaledBitmap(soft(cover), size, size, true)
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    Canvas(out).drawCircle(size / 2f, size / 2f, size / 2f, p)
    return out
  }

  private fun placeholderArt(ctx: Context): Bitmap {
    val size = 128
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFFA06BF0.toInt(), 0xFF6B4FD8.toInt(), Shader.TileMode.CLAMP)
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
    p.color = 0x33FFFFFF
    val half = stroke / 2f
    c.drawRoundRect(RectF(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half), radius, radius, p)
  }

  /** A rounded card: solid colour, with the blurred cover of the playing song laid over it. */
  private fun card(
    e: Env,
    radius: Float,
    fill: Int,
    blur: Bitmap?,
    blurAlpha: Int,
  ): Bitmap {
    val w = e.bgW
    val h = e.bgH
    val r = min(radius, min(w, h) / 2f)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = fill
    c.drawRoundRect(rect, r, r, p)
    if (blur != null && blurAlpha > 0) {
      val q = Paint(Paint.ANTI_ALIAS_FLAG)
      q.shader = BitmapShader(centerCrop(blur, w, h), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      q.alpha = blurAlpha
      c.drawRoundRect(rect, r, r, q)
    }
    border(c, rect, r, e.stroke)
    return out
  }

  /** Cover on top, light panel below (Square widget). [scrims] are dark circles behind the corner icons. */
  private fun squareCard(
    e: Env,
    panelPx: Int,
    light: Int,
    scrims: List<Triple<Float, Float, Float>>,
  ): Bitmap {
    val w = e.bgW
    val h = e.bgH
    val r = min(22f * e.k, min(w, h) / 2f)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    val topH = (h - panelPx).coerceAtLeast(1)
    c.save()
    c.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
    c.drawBitmap(centerCrop(e.st.art ?: placeholderArt(e.ctx), w, topH), 0f, 0f, null)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = light
    c.drawRect(0f, topH.toFloat(), w.toFloat(), h.toFloat(), p)
    p.color = 0x59000000
    for ((cx, cy, rad) in scrims) c.drawCircle(cx, cy, rad, p)
    c.restore()
    border(c, rect, r, e.stroke)
    return out
  }

  /** Progress bar with a round knob (Expanded widget). [barDp] is the width the bar gets in the layout. */
  private fun progressBar(
    e: Env,
    barDp: Int,
  ): Bitmap {
    val d = e.density
    val sc = min(1f, 600f / (barDp * d))
    val w = max(8, (barDp * d * sc).toInt())
    val h = max(8, (16 * d * sc).toInt())
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val knob = 6f * d * sc
    val th = 4f * d * sc
    val cy = h / 2f
    val frac = if (e.st.durMs > 0) (e.st.posMs.toFloat() / e.st.durMs).coerceIn(0f, 1f) else 0f
    val x = knob + (w - 2 * knob) * frac
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0x40FFFFFF
    c.drawRoundRect(RectF(0f, cy - th / 2, w.toFloat(), cy + th / 2), th / 2, th / 2, p)
    p.shader = LinearGradient(0f, 0f, max(1f, x), 0f, 0xFFFF8A65.toInt(), 0xFFFFB199.toInt(), Shader.TileMode.CLAMP)
    c.drawRoundRect(RectF(0f, cy - th / 2, x, cy + th / 2), th / 2, th / 2, p)
    p.shader = null
    p.color = 0xFFFFE3D6.toInt()
    c.drawCircle(x, cy, knob, p)
    p.color = 0xFFFF8A65.toInt()
    p.style = Paint.Style.STROKE
    p.strokeWidth = max(1f, knob * 0.28f)
    c.drawCircle(x, cy, knob - p.strokeWidth / 2f, p)
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
