package app.marlboroadvance.mpvex.ui.browser.music

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** User options for the music player. Saved on the device, so they are still there next time. */
object MusicSettings {
  private var prefs: SharedPreferences? = null

  // ---- Library / audio ----

  /** Songs shorter than this (seconds) are hidden. */
  var minDurationSec by mutableIntStateOf(10)

  /** Bring back the last queue and position when the music player opens. */
  var restoreQueue by mutableStateOf(true)
  var pauseOnUnplug by mutableStateOf(true)

  // ---- Look and feel ----

  /** 0 = open on the tab used last time, 1 = always open on [defaultTab]. */
  var openMode by mutableIntStateOf(0)

  /** Tab shown when [openMode] is 1 (0 For you ... 4 Playlists). */
  var defaultTab by mutableIntStateOf(0)

  /** Tab that was open last time. Saved automatically. */
  var lastTab by mutableIntStateOf(0)

  /** Song sort that was used last time. Saved automatically. */
  var sortBy by mutableIntStateOf(0)
  var sortDesc by mutableStateOf(false)

  /** How solid cards, bars and panels stay when the app uses a transparent custom theme (0-100). */
  var panelOpacity by mutableIntStateOf(65)

  /** 0 = always show labels, 1 = only on the selected tab, 2 = no labels. */
  var tabLabels by mutableIntStateOf(0)
  var showSongDuration by mutableStateOf(true)
  var homeRecent by mutableStateOf(true)
  var homeLastAdded by mutableStateOf(true)
  var homeAlbums by mutableStateOf(true)

  // ---- Now playing ----

  /** 0 = colour gradient from the album art, 1 = plain, 2 = blurred album art */
  var nowPlayingBg by mutableIntStateOf(0)

  /** Strength of the blur for background 2 (5-100). */
  var blurAmount by mutableIntStateOf(50)

  /** 0 = large, 1 = medium, 2 = small */
  var coverSize by mutableIntStateOf(0)
  var keepScreenOn by mutableStateOf(false)
  var swipeCover by mutableStateOf(true)
  var showFavButton by mutableStateOf(true)
  var showExtraControls by mutableStateOf(true)
  var openNowOnPlay by mutableStateOf(true)

  // ---- Personalize / images ----

  /** Which bottom tabs are shown (bit 0 = For you ... bit 4 = Playlists). */
  var tabsMask by mutableIntStateOf(31)

  /** 0 = play + next, 1 = play only, 2 = previous + play + next */
  var miniControls by mutableIntStateOf(0)

  /** Number of columns in the Albums tab (2-4). */
  var gridColumns by mutableIntStateOf(2)

  /** 0 = rounded, 1 = circle, 2 = square */
  var coverShape by mutableIntStateOf(0)
  var showSongArt by mutableStateOf(true)

  /** Search lyrics on the web automatically when a song has none saved. */
  var lyricsAuto by mutableStateOf(true)

  fun isTabVisible(i: Int): Boolean = (tabsMask and (1 shl i)) != 0

  fun firstVisibleTab(): Int = (0..4).firstOrNull { isTabVisible(it) } ?: 0

  /** Turns a bottom tab on or off. At least one tab always stays on. */
  fun setTabVisible(
    i: Int,
    visible: Boolean,
  ) {
    val next = if (visible) tabsMask or (1 shl i) else tabsMask and (1 shl i).inv()
    if (next and 31 != 0) tabsMask = next and 31
  }

  /** The tab the music screen should open on. */
  fun startTab(): Int {
    val wanted = (if (openMode == 0) lastTab else defaultTab).coerceIn(0, 4)
    return if (isTabVisible(wanted)) wanted else firstVisibleTab()
  }

  private fun asMap(): Map<String, Any> =
    linkedMapOf<String, Any>(
      "minDurationSec" to minDurationSec,
      "restoreQueue" to restoreQueue,
      "pauseOnUnplug" to pauseOnUnplug,
      "openMode" to openMode,
      "defaultTab" to defaultTab,
      "lastTab" to lastTab,
      "sortBy" to sortBy,
      "sortDesc" to sortDesc,
      "panelOpacity" to panelOpacity,
      "tabLabels" to tabLabels,
      "showSongDuration" to showSongDuration,
      "homeRecent" to homeRecent,
      "homeLastAdded" to homeLastAdded,
      "homeAlbums" to homeAlbums,
      "nowPlayingBg" to nowPlayingBg,
      "blurAmount" to blurAmount,
      "coverSize" to coverSize,
      "keepScreenOn" to keepScreenOn,
      "swipeCover" to swipeCover,
      "showFavButton" to showFavButton,
      "showExtraControls" to showExtraControls,
      "openNowOnPlay" to openNowOnPlay,
      "tabsMask" to tabsMask,
      "miniControls" to miniControls,
      "gridColumns" to gridColumns,
      "coverShape" to coverShape,
      "showSongArt" to showSongArt,
      "lyricsAuto" to lyricsAuto,
    )

  private fun load(
    i: (String, Int) -> Int,
    b: (String, Boolean) -> Boolean,
  ) {
    minDurationSec = i("minDurationSec", minDurationSec)
    restoreQueue = b("restoreQueue", restoreQueue)
    pauseOnUnplug = b("pauseOnUnplug", pauseOnUnplug)
    openMode = i("openMode", openMode).coerceIn(0, 1)
    defaultTab = i("defaultTab", defaultTab).coerceIn(0, 4)
    lastTab = i("lastTab", lastTab).coerceIn(0, 4)
    sortBy = i("sortBy", sortBy).coerceIn(0, 4)
    sortDesc = b("sortDesc", sortDesc)
    panelOpacity = i("panelOpacity", panelOpacity).coerceIn(0, 100)
    tabLabels = i("tabLabels", tabLabels).coerceIn(0, 2)
    showSongDuration = b("showSongDuration", showSongDuration)
    homeRecent = b("homeRecent", homeRecent)
    homeLastAdded = b("homeLastAdded", homeLastAdded)
    homeAlbums = b("homeAlbums", homeAlbums)
    nowPlayingBg = i("nowPlayingBg", nowPlayingBg).coerceIn(0, 2)
    blurAmount = i("blurAmount", blurAmount).coerceIn(5, 100)
    coverSize = i("coverSize", coverSize).coerceIn(0, 2)
    keepScreenOn = b("keepScreenOn", keepScreenOn)
    swipeCover = b("swipeCover", swipeCover)
    showFavButton = b("showFavButton", showFavButton)
    showExtraControls = b("showExtraControls", showExtraControls)
    openNowOnPlay = b("openNowOnPlay", openNowOnPlay)
    tabsMask = (i("tabsMask", tabsMask) and 31).let { if (it == 0) 31 else it }
    miniControls = i("miniControls", miniControls).coerceIn(0, 2)
    gridColumns = i("gridColumns", gridColumns).coerceIn(2, 4)
    coverShape = i("coverShape", coverShape).coerceIn(0, 2)
    showSongArt = b("showSongArt", showSongArt)
    lyricsAuto = b("lyricsAuto", lyricsAuto)
  }

  fun init(context: Context) {
    if (prefs != null) return
    val p = context.applicationContext.getSharedPreferences("retro_music_settings", Context.MODE_PRIVATE)
    prefs = p
    load({ k, d -> p.getInt(k, d) }, { k, d -> p.getBoolean(k, d) })
  }

  fun save() {
    val e = prefs?.edit() ?: return
    asMap().forEach { (k, v) ->
      when (v) {
        is Int -> e.putInt(k, v)
        is Boolean -> e.putBoolean(k, v)
        else -> {}
      }
    }
    e.apply()
  }

  fun toJson(): JSONObject = JSONObject(asMap())

  /** Used by "Restore": reads the values that are present and keeps the rest as they are. */
  fun applyJson(o: JSONObject) {
    load({ k, d -> o.optInt(k, d) }, { k, d -> o.optBoolean(k, d) })
    save()
  }

  fun reset() {
    minDurationSec = 10
    restoreQueue = true
    pauseOnUnplug = true
    openMode = 0
    defaultTab = 0
    lastTab = 0
    sortBy = 0
    sortDesc = false
    panelOpacity = 65
    tabLabels = 0
    showSongDuration = true
    homeRecent = true
    homeLastAdded = true
    homeAlbums = true
    nowPlayingBg = 0
    blurAmount = 50
    coverSize = 0
    keepScreenOn = false
    swipeCover = true
    showFavButton = true
    showExtraControls = true
    openNowOnPlay = true
    tabsMask = 31
    miniControls = 0
    gridColumns = 2
    coverShape = 0
    showSongArt = true
    lyricsAuto = true
    save()
  }
}
