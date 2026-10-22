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

  /** 0 Default, 1 Colored, 2 Gradient, 3 Blur, 4 Gradient Blur, 5 Glow, 6 Material You */
  var nowPlayingBg by mutableIntStateOf(2)

  /** Strength of the blur for background 2 (5-100). */
  var blurAmount by mutableIntStateOf(50)

  /** 0 = large, 1 = medium, 2 = small */
  var coverSize by mutableIntStateOf(0)
  var keepScreenOn by mutableStateOf(false)
  var swipeCover by mutableStateOf(true)
  var showFavButton by mutableStateOf(true)
  var showExtraControls by mutableStateOf(true)
  var openNowOnPlay by mutableStateOf(false)

  // ---- Personalize / images ----

  /** Which bottom tabs are shown (bit 0 = For you ... bit 4 = Playlists). */
  var tabsMask by mutableIntStateOf(63)

  /** 0 = play + next, 1 = play only, 2 = previous + play + next */
  var miniControls by mutableIntStateOf(0)

  /** Number of columns in the Albums tab (2-4). */
  var gridColumns by mutableIntStateOf(2)

  /** 0 = rounded, 1 = circle, 2 = square */
  var coverShape by mutableIntStateOf(0)
  var showSongArt by mutableStateOf(true)

  /** Search lyrics on the web automatically when a song has none saved. */
  var lyricsAuto by mutableStateOf(true)

  // ---- Themes & colors ----
  /** 0 = system (follow the app), 1 = light, 2 = dark */
  var themeMode by mutableIntStateOf(0)
  var materialYou by mutableStateOf(false)

  /** 0 = none, 1..8 = preset colour theme */
  var themeSeed by mutableIntStateOf(0)
  var extraDark by mutableStateOf(false)

  /** 0 = default, 1..N = colour from the palette */
  var accentIdx by mutableIntStateOf(0)
  var highlightIdx by mutableIntStateOf(0)
  var highlightAlpha by mutableIntStateOf(20)

  // ---- Font ----

  /** 0 = system font, 1 Anime Ace, 2 Source Code Pro, 3 Super Waffles, 4 Stay With Me, 5 = custom file */
  var fontChoice by mutableIntStateOf(0)
  var customFontName by mutableStateOf("")
  var customFontPath by mutableStateOf("")

  /** Text size in percent (70-160). */
  var fontScale by mutableIntStateOf(100)
  var fontBold by mutableStateOf(false)
  var fontItalic by mutableStateOf(false)

  // ---- Now playing extras ----
  var songInfo by mutableStateOf(true)
  var remainingTime by mutableStateOf(false)
  var volumeSlider by mutableStateOf(false)

  // ---- Library ----
  var seeAll by mutableStateOf(true)
  var multiArtists by mutableStateOf(false)
  var artistSeparators by mutableStateOf(",&;")
  var excludedFolders by mutableStateOf("")
  var tabOrder by mutableStateOf("0,1,2,3,4,5")

  /** 0 = no limit, otherwise the number of days */
  var lastAddedDays by mutableIntStateOf(0)

  // ---- Images ----
  var cacheImages by mutableStateOf(true)
  var useMediaStoreArt by mutableStateOf(true)
  var lockArt by mutableStateOf(true)
  var lockBlur by mutableStateOf(false)

  // ---- Audio ----
  var audioFade by mutableStateOf(false)
  var duck by mutableStateOf(true)
  var autoPlay by mutableStateOf(false)
  var keepShuffle by mutableStateOf(false)

  fun excluded(): List<String> = excludedFolders.split("|").filter { it.isNotBlank() }

  /** The bottom tabs in the order the user chose (0 For you, 1 Songs, 2 Albums, 3 Artists, 4 Playlists, 5 Folders). */
  fun tabOrderList(): List<Int> {
    val parsed = tabOrder.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..5 }.distinct()
    return parsed + (0..5).filter { it !in parsed }
  }

  fun isTabVisible(i: Int): Boolean = (tabsMask and (1 shl i)) != 0

  fun firstVisibleTab(): Int = tabOrderList().firstOrNull { isTabVisible(it) } ?: 0

  /** Turns a bottom tab on or off. At least one tab always stays on. */
  fun setTabVisible(
    i: Int,
    visible: Boolean,
  ) {
    val next = if (visible) tabsMask or (1 shl i) else tabsMask and (1 shl i).inv()
    if (next and 63 != 0) tabsMask = next and 63
  }

  /** The tab the music screen should open on. */
  fun startTab(): Int {
    val wanted = (if (openMode == 0) lastTab else defaultTab).coerceIn(0, 5)
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
      "npTheme" to nowPlayingBg,
      "blurAmount" to blurAmount,
      "coverSize" to coverSize,
      "keepScreenOn" to keepScreenOn,
      "swipeCover" to swipeCover,
      "showFavButton" to showFavButton,
      "showExtraControls" to showExtraControls,
      "openNowOnPlay2" to openNowOnPlay,
      "tabsMask2" to tabsMask,
      "miniControls" to miniControls,
      "gridColumns" to gridColumns,
      "coverShape" to coverShape,
      "showSongArt" to showSongArt,
      "lyricsAuto" to lyricsAuto,
      "themeMode" to themeMode,
      "materialYou" to materialYou,
      "themeSeed" to themeSeed,
      "extraDark" to extraDark,
      "accentIdx" to accentIdx,
      "highlightIdx" to highlightIdx,
      "highlightAlpha" to highlightAlpha,
      "fontChoice" to fontChoice,
      "customFontName" to customFontName,
      "customFontPath" to customFontPath,
      "fontScale" to fontScale,
      "fontBold" to fontBold,
      "fontItalic" to fontItalic,
      "songInfo" to songInfo,
      "remainingTime" to remainingTime,
      "volumeSlider" to volumeSlider,
      "seeAll" to seeAll,
      "multiArtists" to multiArtists,
      "artistSeparators" to artistSeparators,
      "excludedFolders" to excludedFolders,
      "tabOrder" to tabOrder,
      "lastAddedDays" to lastAddedDays,
      "cacheImages" to cacheImages,
      "useMediaStoreArt" to useMediaStoreArt,
      "lockArt" to lockArt,
      "lockBlur" to lockBlur,
      "audioFade" to audioFade,
      "duck" to duck,
      "autoPlay" to autoPlay,
      "keepShuffle" to keepShuffle,
    )

  private fun load(
    i: (String, Int) -> Int,
    b: (String, Boolean) -> Boolean,
    str: (String, String) -> String,
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
    nowPlayingBg = i("npTheme", nowPlayingBg).coerceIn(0, 6)
    blurAmount = i("blurAmount", blurAmount).coerceIn(5, 100)
    coverSize = i("coverSize", coverSize).coerceIn(0, 2)
    keepScreenOn = b("keepScreenOn", keepScreenOn)
    swipeCover = b("swipeCover", swipeCover)
    showFavButton = b("showFavButton", showFavButton)
    showExtraControls = b("showExtraControls", showExtraControls)
    openNowOnPlay = b("openNowOnPlay2", openNowOnPlay)
    tabsMask = (i("tabsMask2", tabsMask) and 63).let { if (it == 0) 63 else it }
    miniControls = i("miniControls", miniControls).coerceIn(0, 2)
    gridColumns = i("gridColumns", gridColumns).coerceIn(2, 4)
    coverShape = i("coverShape", coverShape).coerceIn(0, 2)
    showSongArt = b("showSongArt", showSongArt)
    lyricsAuto = b("lyricsAuto", lyricsAuto)
    themeMode = i("themeMode", themeMode).coerceIn(0, 2)
    materialYou = b("materialYou", materialYou)
    themeSeed = i("themeSeed", themeSeed).coerceIn(0, 8)
    extraDark = b("extraDark", extraDark)
    accentIdx = i("accentIdx", accentIdx).coerceIn(0, 20)
    highlightIdx = i("highlightIdx", highlightIdx).coerceIn(0, 20)
    highlightAlpha = i("highlightAlpha", highlightAlpha).coerceIn(0, 100)
    fontChoice = i("fontChoice", fontChoice).coerceIn(0, 5)
    customFontName = str("customFontName", customFontName)
    customFontPath = str("customFontPath", customFontPath)
    fontScale = i("fontScale", fontScale).coerceIn(70, 160)
    fontBold = b("fontBold", fontBold)
    fontItalic = b("fontItalic", fontItalic)
    songInfo = b("songInfo", songInfo)
    remainingTime = b("remainingTime", remainingTime)
    volumeSlider = b("volumeSlider", volumeSlider)
    seeAll = b("seeAll", seeAll)
    multiArtists = b("multiArtists", multiArtists)
    artistSeparators = str("artistSeparators", artistSeparators)
    excludedFolders = str("excludedFolders", excludedFolders)
    tabOrder = str("tabOrder", tabOrder)
    lastAddedDays = i("lastAddedDays", lastAddedDays).coerceIn(0, 3650)
    cacheImages = b("cacheImages", cacheImages)
    useMediaStoreArt = b("useMediaStoreArt", useMediaStoreArt)
    lockArt = b("lockArt", lockArt)
    lockBlur = b("lockBlur", lockBlur)
    audioFade = b("audioFade", audioFade)
    duck = b("duck", duck)
    autoPlay = b("autoPlay", autoPlay)
    keepShuffle = b("keepShuffle", keepShuffle)
  }

  fun init(context: Context) {
    if (prefs != null) return
    val p = context.applicationContext.getSharedPreferences("retro_music_settings", Context.MODE_PRIVATE)
    prefs = p
    load({ k, d -> p.getInt(k, d) }, { k, d -> p.getBoolean(k, d) }, { k, d -> p.getString(k, d) ?: d })
  }

  fun save() {
    val e = prefs?.edit() ?: return
    asMap().forEach { (k, v) ->
      when (v) {
        is Int -> e.putInt(k, v)
        is Boolean -> e.putBoolean(k, v)
        is String -> e.putString(k, v)
        else -> {}
      }
    }
    e.apply()
  }

  fun toJson(): JSONObject = JSONObject(asMap())

  /** Used by "Restore": reads the values that are present and keeps the rest as they are. */
  fun applyJson(o: JSONObject) {
    load({ k, d -> o.optInt(k, d) }, { k, d -> o.optBoolean(k, d) }, { k, d -> o.optString(k, d) })
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
    nowPlayingBg = 2
    blurAmount = 50
    coverSize = 0
    keepScreenOn = false
    swipeCover = true
    showFavButton = true
    showExtraControls = true
    openNowOnPlay = false
    tabsMask = 63
    miniControls = 0
    gridColumns = 2
    coverShape = 0
    showSongArt = true
    lyricsAuto = true
    themeMode = 0
    materialYou = false
    themeSeed = 0
    extraDark = false
    accentIdx = 0
    highlightIdx = 0
    highlightAlpha = 20
    fontChoice = 0
    fontScale = 100
    fontBold = false
    fontItalic = false
    songInfo = true
    remainingTime = false
    volumeSlider = false
    seeAll = true
    multiArtists = false
    artistSeparators = ",&;"
    excludedFolders = ""
    tabOrder = "0,1,2,3,4,5"
    lastAddedDays = 0
    cacheImages = true
    useMediaStoreArt = true
    lockArt = true
    lockBlur = false
    audioFade = false
    duck = true
    autoPlay = false
    keepShuffle = false
    save()
  }
}
