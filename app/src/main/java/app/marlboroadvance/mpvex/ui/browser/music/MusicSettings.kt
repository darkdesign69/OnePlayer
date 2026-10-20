package app.marlboroadvance.mpvex.ui.browser.music

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** User options for the music player. Saved on the device. */
object MusicSettings {
  private var prefs: SharedPreferences? = null

  /** Songs shorter than this (seconds) are hidden. */
  var minDurationSec by mutableIntStateOf(10)

  /** Number of columns in the Albums tab (2-4). */
  var gridColumns by mutableIntStateOf(2)

  /** 0 = rounded, 1 = circle, 2 = square */
  var coverShape by mutableIntStateOf(0)

  /** 0 = color gradient from the album art, 1 = plain */
  var nowPlayingBg by mutableIntStateOf(0)

  /** Tab shown when the music screen opens (0 For you ... 4 Playlists). */
  var defaultTab by mutableIntStateOf(0)
  var keepScreenOn by mutableStateOf(false)
  var pauseOnUnplug by mutableStateOf(true)
  var swipeCover by mutableStateOf(true)

  fun init(context: Context) {
    if (prefs != null) return
    val p = context.applicationContext.getSharedPreferences("retro_music_settings", Context.MODE_PRIVATE)
    prefs = p
    minDurationSec = p.getInt("minDurationSec", 10)
    gridColumns = p.getInt("gridColumns", 2)
    coverShape = p.getInt("coverShape", 0)
    nowPlayingBg = p.getInt("nowPlayingBg", 0)
    defaultTab = p.getInt("defaultTab", 0)
    keepScreenOn = p.getBoolean("keepScreenOn", false)
    pauseOnUnplug = p.getBoolean("pauseOnUnplug", true)
    swipeCover = p.getBoolean("swipeCover", true)
  }

  fun save() {
    prefs
      ?.edit()
      ?.putInt("minDurationSec", minDurationSec)
      ?.putInt("gridColumns", gridColumns)
      ?.putInt("coverShape", coverShape)
      ?.putInt("nowPlayingBg", nowPlayingBg)
      ?.putInt("defaultTab", defaultTab)
      ?.putBoolean("keepScreenOn", keepScreenOn)
      ?.putBoolean("pauseOnUnplug", pauseOnUnplug)
      ?.putBoolean("swipeCover", swipeCover)
      ?.apply()
  }

  fun reset() {
    minDurationSec = 10
    gridColumns = 2
    coverShape = 0
    nowPlayingBg = 0
    defaultTab = 0
    keepScreenOn = false
    pauseOnUnplug = true
    swipeCover = true
    save()
  }
}
