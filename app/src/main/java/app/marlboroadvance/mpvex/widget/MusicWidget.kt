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
import android.graphics.LinearGradient
import android.graphics.Path
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

/** The seven widgets in the picker (the same designs as Oto Music). Each starts at its own size and resizes freely. */
enum class MusicWidgetKind(
  val defaultWidthDp: Int,
  val defaultHeightDp: Int,
) {
  Card(250, 74),
  Md2(250, 110),
  Md3(160, 150),
  Md4(250, 76),
  Md5(150, 150),
  Text(160, 90),
  TextImage(160, 130),
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

class MusicWidgetCardProvider : MusicWidgetProvider()

class MusicWidgetMd2Provider : MusicWidgetProvider()

class MusicWidgetMd3Provider : MusicWidgetProvider()

class MusicWidgetMd4Provider : MusicWidgetProvider()

class MusicWidgetMd5Provider : MusicWidgetProvider()

class MusicWidgetTextProvider : MusicWidgetProvider()

class MusicWidgetTextImageProvider : MusicWidgetProvider()

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
      MusicWidgetKind.Card to MusicWidgetCardProvider::class.java,
      MusicWidgetKind.Md2 to MusicWidgetMd2Provider::class.java,
      MusicWidgetKind.Md3 to MusicWidgetMd3Provider::class.java,
      MusicWidgetKind.Md4 to MusicWidgetMd4Provider::class.java,
      MusicWidgetKind.Md5 to MusicWidgetMd5Provider::class.java,
      MusicWidgetKind.Text to MusicWidgetTextProvider::class.java,
      MusicWidgetKind.TextImage to MusicWidgetTextImageProvider::class.java,
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

  private enum class Btn { Shuffle, Fav, Prev, Play, Next, Repeat }

  private enum class Lay { Card, Md2, Vert, Md4, Md5, Text, TextImage }

  /** Colours of one widget. The accent comes from the cover, like Material You. */
  private class Pal(
    val bg: Int,
    val title: Int,
    val sub: Int,
    val primary: Int,
    val onPrimary: Int,
    val surfaceVar: Int,
    val onVar: Int,
    val plain: Int,
    val active: Int,
  )

  private var accentFor: Bitmap? = null
  private var accentCache = 0

  /** The most colourful part of the cover (0 when the cover has no real colour). */
  private fun coverColor(art: Bitmap?): Int {
    if (art == null) return 0
    if (accentFor !== art) {
      accentFor = art
      accentCache =
        try {
          val small = Bitmap.createScaledBitmap(soft(art), 24, 24, true)
          var best = 0
          var bestScore = -1f
          val hsl = FloatArray(3)
          for (y in 0 until 24) {
            for (x in 0 until 24) {
              val c = small.getPixel(x, y)
              ColorUtils.colorToHSL(c, hsl)
              val score = hsl[1] * (1f - kotlin.math.abs(hsl[2] - 0.5f) * 1.4f)
              if (score > bestScore) {
                bestScore = score
                best = c
              }
            }
          }
          if (bestScore < 0.12f) 0 else best
        } catch (_: Throwable) {
          0
        }
    }
    return accentCache
  }

  private fun palette(
    ctx: Context,
    kind: MusicWidgetKind,
    art: Bitmap?,
  ): Pal {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val cc = coverColor(art)
    val hsl = FloatArray(3)
    fun tone(l: Float): Int {
      if (cc == 0) return if (l > 0.5f) 0xFFD0BCFF.toInt() else 0xFF6750A4.toInt()
      ColorUtils.colorToHSL(cc, hsl)
      return ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceIn(0.35f, 0.9f), l))
    }
    val seed = if (cc == 0) 0xFF6750A4.toInt() else tone(0.5f)
    return when (kind) {
      MusicWidgetKind.Card ->
        Pal(0xFFBDC1C6.toInt(), 0xDD000000.toInt(), 0x99000000.toInt(), 0xFF3C4043.toInt(), 0xFFFFFFFF.toInt(), 0x22000000, 0xCC202124.toInt(), 0xCC202124.toInt(), 0xFF1A73E8.toInt())
      MusicWidgetKind.Md5 ->
        Pal(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xE6FFFFFF.toInt(), tone(0.8f), 0xFF1C1B1F.toInt(), 0x66000000, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), tone(0.8f))
      MusicWidgetKind.Text, MusicWidgetKind.TextImage ->
        Pal(0, 0xFFFFFFFF.toInt(), 0xE6FFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF1C1B1F.toInt(), 0x33000000, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF8AB4FF.toInt())
      else ->
        if (dark) {
          val base = 0xFF1C1B1F.toInt()
          Pal(ColorUtils.blendARGB(base, seed, 0.12f), 0xFFE6E1E5.toInt(), 0xFFCAC4D0.toInt(), tone(0.78f), 0xFF1C1B1F.toInt(), ColorUtils.blendARGB(base, seed, 0.28f), 0xFFE6E1E5.toInt(), 0xFFE6E1E5.toInt(), tone(0.78f))
        } else {
          val base = 0xFFFFFBFE.toInt()
          Pal(ColorUtils.blendARGB(base, seed, 0.10f), 0xFF1C1B1F.toInt(), 0xFF49454F.toInt(), tone(0.40f), 0xFFFFFFFF.toInt(), ColorUtils.blendARGB(base, seed, 0.22f), 0xFF1C1B1F.toInt(), 0xFF1C1B1F.toInt(), tone(0.40f))
        }
    }
  }

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
    val tall = hDp * 1.2f > wDp

    // the design follows the widget's shape, so every widget works at any size
    val lay =
      when (kind) {
        MusicWidgetKind.Card -> if (tall) Lay.Vert else Lay.Card
        MusicWidgetKind.Md2 -> if (tall) Lay.Vert else if (hDp < 92) Lay.Card else Lay.Md2
        MusicWidgetKind.Md3 -> if (wDp > hDp * 1.7f) (if (hDp < 92) Lay.Card else Lay.Md2) else Lay.Vert
        MusicWidgetKind.Md4 -> if (tall) Lay.Vert else Lay.Md4
        MusicWidgetKind.Md5 -> Lay.Md5
        MusicWidgetKind.Text -> Lay.Text
        MusicWidgetKind.TextImage -> Lay.TextImage
      }
    val layout =
      when (lay) {
        Lay.Card -> R.layout.widget_oto_card
        Lay.Md2 -> R.layout.widget_oto_md2
        Lay.Vert -> R.layout.widget_oto_vert
        Lay.Md4 -> R.layout.widget_oto_md4
        Lay.Md5 -> R.layout.widget_oto_md5
        Lay.Text -> R.layout.widget_oto_text
        Lay.TextImage -> R.layout.widget_oto_textimage
      }
    val rv = RemoteViews(ctx.packageName, layout)
    val g = Geo(density, wDp, hDp, min(1f, 480f / max(wDp, hDp) / density))
    val cover = st.art ?: placeholder ?: placeholderArt(ctx).also { placeholder = it }
    val pal = palette(ctx, kind, st.art)
    val title = if (st.hasTrack) st.title else ctx.getString(R.string.music_widget_idle_title)
    val artist = if (st.hasTrack) st.artist else ctx.getString(R.string.music_widget_idle_artist)
    val ratio = if (st.durMs > 0) (st.posMs.toFloat() / st.durMs).coerceIn(0f, 1f) else 0f

    val showArt: Boolean
    val showTitle: Boolean
    val showArtist: Boolean
    var oneLine = false
    val priority: List<Btn>
    val count: Int
    val bgRadius = when (kind) {
      MusicWidgetKind.Card -> 2f
      MusicWidgetKind.Md2 -> 20f
      MusicWidgetKind.Md3 -> 24f
      MusicWidgetKind.Md4 -> 24f
      else -> 24f
    }

    when (lay) {
      Lay.Card -> {
        val side = hDp
        val panelW = wDp - side - 18
        showArt = wDp >= 150 && side >= 40
        val w = if (showArt) panelW else wDp - 18
        count = (w / 36).coerceIn(3, 6)
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Fav, Btn.Repeat, Btn.Shuffle)
        showTitle = hDp >= 62
        showArtist = false
        oneLine = true
        val r = if (kind == MusicWidgetKind.Card) 2f else bgRadius
        val frac = r / max(side, 1)
        if (showArt) rv.setImageViewBitmap(R.id.o_art, artBitmap(cover, frac, leftOnly = true))
        rv.setImageViewBitmap(R.id.o_bg, surfaceBg(g, pal.bg, bgRadius, ratio, pal.primary, pal.surfaceVar))
      }
      Lay.Md2 -> {
        val side = hDp - 20
        val full = wDp - 20 - (side + 12)
        showArt = wDp >= 170 && side >= 44 && full >= 146
        val colW = if (showArt) full else wDp - 20
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Fav, Btn.Repeat, Btn.Shuffle)
        count = fixedCount(colW, listOf(50, 48, 48, 40, 36, 36))
        showTitle = hDp >= 92
        showArtist = hDp >= 112
        if (showArt) rv.setImageViewBitmap(R.id.o_art, artBitmap(cover, 0.17f, leftOnly = false))
        rv.setImageViewBitmap(R.id.o_bg, surfaceBg(g, pal.bg, bgRadius, ratio, pal.primary, pal.surfaceVar))
      }
      Lay.Vert -> {
        // greedily keep what fits: buttons first, then the cover, the title, the artist
        var avail = hDp - 68
        showTitle = avail - 20 >= 32
        if (showTitle) avail -= 20
        showArtist = showTitle && avail - 15 >= 32
        if (showArtist) avail -= 15
        showArt = avail >= 30
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Fav, Btn.Repeat, Btn.Shuffle)
        count = ((wDp - 16) / 44).coerceIn(3, 6)
        if (showArt) rv.setImageViewBitmap(R.id.o_art, artBitmap(cover, 0.42f, leftOnly = false))
        rv.setImageViewBitmap(R.id.o_bg, surfaceBg(g, pal.bg, bgRadius, ratio, pal.primary, pal.surfaceVar))
      }
      Lay.Md4 -> {
        val side = hDp - 12
        showArt = wDp >= 200 && side >= 44
        val room = wDp - 12 - (if (showArt) side + 10 else 0) - 70
        priority = listOf(Btn.Play, Btn.Fav, Btn.Prev, Btn.Next, Btn.Repeat, Btn.Shuffle)
        count = fixedCount(room, listOf(72, 44, 40, 40, 36, 36))
        showTitle = hDp >= 40
        showArtist = hDp >= 62
        if (showArt) rv.setImageViewBitmap(R.id.o_art, artBitmap(cover, 0.25f, leftOnly = false))
        rv.setImageViewBitmap(R.id.o_bg, surfaceBg(g, pal.bg, bgRadius, ratio, pal.primary, pal.surfaceVar))
      }
      Lay.Md5 -> {
        showArt = false
        showTitle = hDp >= 84
        showArtist = hDp >= 106
        priority = listOf(Btn.Play, Btn.Fav, Btn.Prev, Btn.Next, Btn.Repeat, Btn.Shuffle)
        count = if (wDp >= 260) 6 else if (wDp >= 180) 4 else 2
        rv.setImageViewBitmap(R.id.o_bg, fullArtBg(g, cover, bgRadius, ratio))
      }
      Lay.Text -> {
        showArt = false
        showTitle = true
        showArtist = hDp >= 72
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Fav, Btn.Shuffle, Btn.Repeat)
        count = (wDp / 50).coerceIn(3, 6)
      }
      Lay.TextImage -> {
        var avail = hDp - 66
        showTitle = true
        showArtist = avail >= 60
        showArt = avail - 44 >= 30
        priority = listOf(Btn.Play, Btn.Prev, Btn.Next, Btn.Fav, Btn.Shuffle, Btn.Repeat)
        count = (wDp / 50).coerceIn(3, 6)
        if (showArt) rv.setImageViewBitmap(R.id.o_art, artBitmap(cover, 0.2f, leftOnly = false))
      }
    }
    if (!showArt) rv.setViewVisibility(R.id.o_art, View.GONE) else rv.setViewVisibility(R.id.o_art, View.VISIBLE)

    // text
    val line = if (oneLine && artist.isNotBlank() && st.hasTrack) "$title - $artist" else title
    rv.setTextViewText(R.id.o_title, line)
    rv.setTextViewText(R.id.o_artist, artist)
    rv.setTextColor(R.id.o_title, pal.title)
    rv.setTextColor(R.id.o_artist, pal.sub)
    val tSize =
      when (lay) {
        Lay.Text -> (min(wDp / 8f, hDp / 3.4f)).coerceIn(14f, 26f)
        Lay.TextImage -> (min(wDp / 9f, hDp / 6f)).coerceIn(13f, 20f)
        Lay.Card -> 13f
        Lay.Md4 -> 15f
        else -> 16f
      }
    rv.setTextViewTextSize(R.id.o_title, TypedValue.COMPLEX_UNIT_DIP, tSize)
    rv.setTextViewTextSize(R.id.o_artist, TypedValue.COMPLEX_UNIT_DIP, (tSize * 0.76f).coerceAtLeast(11f))
    rv.setViewVisibility(R.id.o_title_area, if (showTitle) View.VISIBLE else View.GONE)
    rv.setViewVisibility(R.id.o_artist, if (showArtist && !oneLine) View.VISIBLE else View.GONE)

    // buttons
    val shown = priority.take(count).toSet()
    for (b in Btn.values()) {
      rv.setViewVisibility(slotId(b), if (b in shown) View.VISIBLE else View.GONE)
      if (b in shown) drawButton(ctx, rv, b, lay, kind, pal, st)
    }
    rv.setOnClickPendingIntent(R.id.o_play, service(ctx, MusicPlaybackService.ACTION_TOGGLE, 101))
    rv.setOnClickPendingIntent(R.id.o_prev, service(ctx, MusicPlaybackService.ACTION_PREV, 102))
    rv.setOnClickPendingIntent(R.id.o_next, service(ctx, MusicPlaybackService.ACTION_NEXT, 103))
    rv.setOnClickPendingIntent(R.id.o_fav, broadcast(ctx, MusicWidgetActionReceiver.ACTION_FAVORITE, 104))
    rv.setOnClickPendingIntent(R.id.o_repeat, broadcast(ctx, MusicWidgetActionReceiver.ACTION_REPEAT, 105))
    rv.setOnClickPendingIntent(R.id.o_shuffle, broadcast(ctx, MusicWidgetActionReceiver.ACTION_SHUFFLE, 106))

    // everything else (background, cover, title) opens the player on the Now Playing screen
    val open = openApp(ctx)
    rv.setOnClickPendingIntent(R.id.o_root, open)
    rv.setOnClickPendingIntent(R.id.o_title_area, open)
    rv.setOnClickPendingIntent(R.id.o_art, open)
    return rv
  }

  /** How many buttons of the given fixed widths fit, always at least three. */
  private fun fixedCount(
    room: Int,
    widths: List<Int>,
  ): Int {
    var sum = 0
    var n = 0
    for (w in widths) {
      if (sum + w > room && n >= 3) break
      sum += w
      n++
    }
    return n.coerceAtLeast(3)
  }

  private fun slotId(b: Btn) =
    when (b) {
      Btn.Shuffle -> R.id.o_shuffle
      Btn.Fav -> R.id.o_fav
      Btn.Prev -> R.id.o_prev
      Btn.Play -> R.id.o_play
      Btn.Next -> R.id.o_next
      Btn.Repeat -> R.id.o_repeat
    }

  private fun iconId(b: Btn) =
    when (b) {
      Btn.Shuffle -> R.id.o_shuffle_icon
      Btn.Fav -> R.id.o_fav_icon
      Btn.Prev -> R.id.o_prev_icon
      Btn.Play -> R.id.o_play_icon
      Btn.Next -> R.id.o_next_icon
      Btn.Repeat -> R.id.o_repeat_icon
    }

  /** Each button is a small bitmap: the icon on a circle, a pill, a rounded box, or bare. */
  private fun drawButton(
    ctx: Context,
    rv: RemoteViews,
    b: Btn,
    lay: Lay,
    kind: MusicWidgetKind,
    pal: Pal,
    st: State,
  ) {
    val res =
      when (b) {
        Btn.Shuffle -> R.drawable.wg_shuffle
        Btn.Fav -> favRes(st.fav)
        Btn.Prev -> R.drawable.wg_prev
        Btn.Play -> if (st.playing) R.drawable.wg_pause else R.drawable.wg_play
        Btn.Next -> R.drawable.wg_next
        Btn.Repeat -> repeatRes(st.repeat)
      }
    val on =
      when (b) {
        Btn.Shuffle -> st.shuffle
        Btn.Repeat -> st.repeat != 0
        else -> false
      }
    val plainTint = if (b == Btn.Fav && st.fav) FAV_ON else if (on) pal.active else pal.plain
    // shape: 0 none, 1 circle, 2 pill (fully round), 3 rounded box
    var shape = 0
    var fill = 0
    var tint = plainTint
    var w = 40f
    var h = 40f
    var icon = 24f
    val designed = lay == Lay.Md2 || lay == Lay.Vert || lay == Lay.Md4 || lay == Lay.Md5
    if (designed) {
      when (b) {
        Btn.Play -> {
          fill = pal.primary
          tint = pal.onPrimary
          when (lay) {
            Lay.Md2 -> { shape = 1; w = 50f; h = 50f; icon = 26f }
            Lay.Vert -> { shape = 2; w = 124f; h = 44f; icon = 26f }
            Lay.Md4 -> { shape = 3; w = 72f; h = 46f; icon = 26f }
            else -> { shape = 3; w = 110f; h = 40f; icon = 24f }
          }
        }
        Btn.Prev, Btn.Next ->
          if (lay == Lay.Vert || lay == Lay.Md4 || lay == Lay.Md5) {
            shape = 1
            fill = pal.surfaceVar
            tint = pal.onVar
            w = if (lay == Lay.Md4) 40f else 38f
            h = w
          }
        Btn.Fav -> if (lay == Lay.Md4) { shape = 3; fill = pal.surfaceVar; tint = if (st.fav) FAV_ON else pal.onVar; w = 44f; h = 44f } else if (lay == Lay.Md5) { shape = 1; fill = pal.surfaceVar; tint = if (st.fav) FAV_ON else pal.onVar; w = 40f; h = 40f }
        else -> Unit
      }
    } else if (b == Btn.Play) {
      icon = if (lay == Lay.Card) 30f else 36f
      w = 48f
      h = 48f
    }
    rv.setImageViewBitmap(iconId(b), buttonBitmap(ctx, res, shape, fill, tint, w, h, icon))
  }

  private fun buttonBitmap(
    ctx: Context,
    res: Int,
    shape: Int,
    fill: Int,
    tint: Int,
    wDp: Float,
    hDp: Float,
    iconDp: Float,
  ): Bitmap {
    val d = min(ctx.resources.displayMetrics.density, 3f)
    val w = max(8, (wDp * d).toInt())
    val h = max(8, (hDp * d).toInt())
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    if (shape != 0) {
      val p = Paint(Paint.ANTI_ALIAS_FLAG)
      p.color = fill
      val r = if (shape == 3) 16f * d else min(w, h) / 2f
      c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, p)
    }
    ContextCompat.getDrawable(ctx, res)?.mutate()?.let {
      val s = (iconDp * d).toInt()
      val l = (w - s) / 2
      val t = (h - s) / 2
      it.setTint(tint)
      it.setBounds(l, t, l + s, t + s)
      it.draw(c)
    }
    return out
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

  private fun favRes(on: Boolean) = if (on) R.drawable.wg_fav_on else R.drawable.wg_fav

  private fun repeatRes(mode: Int) =
    when (mode) {
      1 -> R.drawable.wg_repeat_all
      2 -> R.drawable.wg_repeat_one
      else -> R.drawable.wg_repeat_off
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

  // ---- artwork (its own view, so it always sits beside the text) and backgrounds ----

  /** Square cover with rounded corners; [frac] is the corner radius as a part of the side. */
  private fun artBitmap(
    cover: Bitmap,
    frac: Float,
    leftOnly: Boolean,
  ): Bitmap {
    val s = 256
    val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    p.shader = BitmapShader(centerCrop(cover, s, s), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val r = s * frac.coerceIn(0f, 0.5f)
    val rr = if (leftOnly) floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r) else FloatArray(8) { r }
    val path = Path()
    path.addRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), rr, Path.Direction.CW)
    Canvas(out).drawPath(path, p)
    return out
  }

  private fun maskRound(
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

  private fun progressLine(
    c: Canvas,
    g: Geo,
    ratio: Float,
    track: Int,
    fill: Int,
  ) {
    val l = g.b(14f)
    val r = g.bw - l
    val h = max(2f, g.b(3f))
    val bottom = g.bh - g.b(4f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = track
    c.drawRoundRect(RectF(l, bottom - h, r, bottom), h / 2, h / 2, p)
    if (ratio > 0f) {
      p.color = fill
      c.drawRoundRect(RectF(l, bottom - h, l + (r - l) * ratio, bottom), h / 2, h / 2, p)
    }
  }

  /** Flat rounded card in the widget's surface colour, with a thin play line along the bottom. */
  private fun surfaceBg(
    g: Geo,
    color: Int,
    radiusDp: Float,
    ratio: Float,
    fill: Int,
    track: Int,
  ): Bitmap {
    val bmp = Bitmap.createBitmap(g.bw, g.bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(ColorUtils.setAlphaComponent(color, 0xF2))
    progressLine(c, g, ratio, ColorUtils.setAlphaComponent(track, 0x99), fill)
    maskRound(bmp, g, radiusDp)
    return bmp
  }

  /** The cover fills the whole widget, with a dark veil so the text stays readable. */
  private fun fullArtBg(
    g: Geo,
    cover: Bitmap,
    radiusDp: Float,
    ratio: Float,
  ): Bitmap {
    val bmp = Bitmap.createBitmap(g.bw, g.bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawBitmap(centerCrop(cover, g.bw, g.bh), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    val p = Paint()
    p.shader = LinearGradient(0f, g.bh * 0.35f, 0f, g.bh.toFloat(), 0x14000000, 0xA0000000.toInt(), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, g.bw.toFloat(), g.bh.toFloat(), p)
    c.drawColor(0x33000000)
    progressLine(c, g, ratio, 0x44FFFFFF, 0xFFFFFFFF.toInt())
    maskRound(bmp, g, radiusDp)
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
