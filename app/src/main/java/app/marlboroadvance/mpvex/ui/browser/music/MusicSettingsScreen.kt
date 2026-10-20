package app.marlboroadvance.mpvex.ui.browser.music

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

// ---------------------------------------------------------------------------------------------
// Theme helpers for the music screens
// ---------------------------------------------------------------------------------------------

private fun Color.withMinAlpha(a: Float): Color = if (alpha < a) copy(alpha = a) else this

/**
 * When the app uses a see-through custom theme, cards, bars and panels can become invisible.
 * This keeps them at least as solid as the "Card & bar opacity" music setting.
 */
@Composable
internal fun MusicPanelTheme(content: @Composable () -> Unit) {
  val cs: ColorScheme = MaterialTheme.colorScheme
  if (cs.surface.alpha >= 0.99f) {
    content()
  } else {
    val floor = (MusicSettings.panelOpacity / 100f).coerceIn(0f, 1f)
    val accentFloor = maxOf(floor, 0.75f)
    val fixed =
      cs.copy(
        surfaceDim = cs.surfaceDim.withMinAlpha(floor),
        surfaceContainerLowest = cs.surfaceContainerLowest.withMinAlpha(floor),
        surfaceContainerLow = cs.surfaceContainerLow.withMinAlpha(floor),
        surfaceContainer = cs.surfaceContainer.withMinAlpha(floor),
        surfaceVariant = cs.surfaceVariant.withMinAlpha(floor),
        primaryContainer = cs.primaryContainer.withMinAlpha(accentFloor),
        secondaryContainer = cs.secondaryContainer.withMinAlpha(accentFloor),
        tertiaryContainer = cs.tertiaryContainer.withMinAlpha(accentFloor),
      )
    MaterialTheme(colorScheme = fixed, content = content)
  }
}

/** A solid colour for full-screen layers such as Now playing (never see-through). */
@Composable
internal fun solidSurface(): Color {
  val cs = MaterialTheme.colorScheme
  val s = cs.surface
  if (s.alpha >= 0.99f) return s
  val dark = cs.onSurface.luminance() > 0.5f
  val base = if (dark) Color(0xFF121212) else Color(0xFFFFFBFE)
  return lerp(base, cs.primary, 0.08f)
}

// ---------------------------------------------------------------------------------------------
// Settings hub + pages (same groups as Retro Music Player)
// ---------------------------------------------------------------------------------------------

internal val musicSettingsTitles =
  listOf("Look and feel", "Now playing", "Audio", "Personalize", "Images", "Other", "Backup & Restore", "About")

private class HubItem(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val circle: Color,
  val tint: Color,
)

private val hubItems =
  listOf(
    HubItem("Look and feel", "Change the theme and colors of the app", Icons.Filled.Palette, Color(0xFF1A2B52), Color(0xFF2F6FE0)),
    HubItem("Now playing", "Customize the now playing screen", Icons.Filled.PlayCircleOutline, Color(0xFF4A0F34), Color(0xFFE0197D)),
    HubItem("Audio", "Change the sound settings and adjust the equalizer controls", Icons.AutoMirrored.Filled.VolumeUp, Color(0xFF1A1B5A), Color(0xFF3C4AE0)),
    HubItem("Personalize", "Customize your now playing and UI controls", Icons.Filled.ToggleOn, Color(0xFF0E4A4A), Color(0xFF00BFA5)),
    HubItem("Images", "Change album art and cover settings", Icons.Filled.Image, Color(0xFF4A1A24), Color(0xFFE0334D)),
    HubItem("Other", "Advanced testing features", Icons.Filled.Category, Color(0xFF14275A), Color(0xFF2F5FE0)),
    HubItem("Backup & Restore", "Backup and restore your settings, playlists", Icons.Filled.Restore, Color(0xFF1F4050), Color(0xFF3AA0D0)),
    HubItem("About", "Version and info", Icons.Filled.Info, Color(0xFF1F4A2C), Color(0xFF3DD16F)),
  )

private fun msSave(block: () -> Unit) {
  block()
  MusicSettings.save()
}

@Composable
internal fun MusicSettingsHost(
  page: Int,
  onPage: (Int) -> Unit,
  onScan: () -> Unit,
  onClearHistory: () -> Unit,
) {
  when (page) {
    0 -> LookAndFeelPage()
    1 -> NowPlayingPage()
    2 -> AudioPage()
    3 -> PersonalizePage()
    4 -> ImagesPage()
    5 -> OtherPage(onScan, onClearHistory)
    6 -> BackupPage()
    7 -> AboutPage()
    else -> SettingsHub(onPage)
  }
}

@Composable
private fun SettingsHub(onPage: (Int) -> Unit) {
  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
    hubItems.forEachIndexed { i, h ->
      item {
        Row(
          modifier = Modifier.fillMaxWidth().clickable { onPage(i) }.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          Box(
            modifier = Modifier.size(62.dp).clip(CircleShape).background(h.circle),
            contentAlignment = Alignment.Center,
          ) { Icon(h.icon, null, tint = h.tint, modifier = Modifier.size(28.dp)) }
          Column(modifier = Modifier.weight(1f)) {
            Text(h.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(h.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
          }
        }
      }
    }
  }
}

@Composable
private fun PageList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 24.dp),
    content = content,
  )
}

// ---- Look and feel ----

@Composable
private fun LookAndFeelPage() {
  PageList {
    item { MsHeader("Theme") }
    item {
      MsNote("App theme, accent colour and the custom background picture are set in the main app Settings (Customize theme). Music uses them automatically.")
    }
    item {
      MsSlider("Card & bar opacity", MusicSettings.panelOpacity, 0..100, "%", "Keeps cards, bars and panels visible when the custom theme transparency is high") {
        MusicSettings.panelOpacity = it
      }
    }
    item { MsHeader("Start screen") }
    item {
      MsChoice("Open music on", listOf("Last used tab", "Fixed tab"), MusicSettings.openMode) { msSave { MusicSettings.openMode = it } }
    }
    if (MusicSettings.openMode == 1) {
      item {
        MsChoice("Fixed tab", listOf("For you", "Songs", "Albums", "Artists", "Playlists"), MusicSettings.defaultTab) {
          msSave { MusicSettings.defaultTab = it }
        }
      }
    }
    item { MsHeader("Bottom bar") }
    item {
      MsChoice("Tab labels", listOf("Always", "Selected only", "Hidden"), MusicSettings.tabLabels) { msSave { MusicSettings.tabLabels = it } }
    }
    item { MsHeader("Lists") }
    item { MsSwitch("Show song duration", MusicSettings.showSongDuration) { msSave { MusicSettings.showSongDuration = it } } }
    item { MsHeader("For you page") }
    item { MsSwitch("Recently played", MusicSettings.homeRecent) { msSave { MusicSettings.homeRecent = it } } }
    item { MsSwitch("Last added", MusicSettings.homeLastAdded) { msSave { MusicSettings.homeLastAdded = it } } }
    item { MsSwitch("Albums", MusicSettings.homeAlbums) { msSave { MusicSettings.homeAlbums = it } } }
  }
}

// ---- Now playing ----

@Composable
private fun NowPlayingPage() {
  PageList {
    item { MsHeader("Appearance") }
    item {
      MsChoice("Background", listOf("Album color", "Plain", "Blurred cover"), MusicSettings.nowPlayingBg) {
        msSave { MusicSettings.nowPlayingBg = it }
      }
    }
    if (MusicSettings.nowPlayingBg == 2) {
      item {
        MsSlider("Blur amount", MusicSettings.blurAmount, 5..100, "", "Needs Android 12 or newer to blur") {
          MusicSettings.blurAmount = it
        }
      }
    }
    item {
      MsChoice("Cover size", listOf("Large", "Medium", "Small"), MusicSettings.coverSize) { msSave { MusicSettings.coverSize = it } }
    }
    item { MsHeader("Controls") }
    item { MsSwitch("Swipe cover to change song", MusicSettings.swipeCover) { msSave { MusicSettings.swipeCover = it } } }
    item { MsSwitch("Show favorite button", MusicSettings.showFavButton) { msSave { MusicSettings.showFavButton = it } } }
    item { MsSwitch("Show extra controls (queue, timer, speed, equalizer)", MusicSettings.showExtraControls) { msSave { MusicSettings.showExtraControls = it } } }
    item { MsHeader("Behavior") }
    item { MsSwitch("Open Now playing when a song starts", MusicSettings.openNowOnPlay) { msSave { MusicSettings.openNowOnPlay = it } } }
    item { MsSwitch("Keep screen on in Now playing", MusicSettings.keepScreenOn) { msSave { MusicSettings.keepScreenOn = it } } }
  }
}

// ---- Audio ----

@Composable
private fun AudioPage() {
  val context = LocalContext.current
  PageList {
    item { MsHeader("Sound") }
    item { MsAction("Equalizer", "Open the equalizer of your phone") { openEqualizer(context) } }
    item { MsHeader("Playback") }
    item { MsSwitch("Pause when headphones disconnect", MusicSettings.pauseOnUnplug) { msSave { MusicSettings.pauseOnUnplug = it } } }
    item { MsSwitch("Remember queue and position", MusicSettings.restoreQueue) { msSave { MusicSettings.restoreQueue = it } } }
    item { MsHeader("Library") }
    item {
      MsChoice(
        "Hide songs shorter than",
        listOf("Off", "10 sec", "30 sec", "60 sec"),
        listOf(0, 10, 30, 60).indexOf(MusicSettings.minDurationSec).coerceAtLeast(0),
      ) { msSave { MusicSettings.minDurationSec = listOf(0, 10, 30, 60)[it] } }
    }
  }
}

// ---- Personalize ----

@Composable
private fun PersonalizePage() {
  val names = listOf("For you", "Songs", "Albums", "Artists", "Playlists")
  PageList {
    item { MsHeader("Bottom bar tabs") }
    names.forEachIndexed { i, n ->
      item { MsSwitch(n, MusicSettings.isTabVisible(i)) { msSave { MusicSettings.setTabVisible(i, it) } } }
    }
    item { MsHeader("Mini player") }
    item {
      MsChoice("Buttons", listOf("Play + Next", "Play only", "Previous + Play + Next"), MusicSettings.miniControls) {
        msSave { MusicSettings.miniControls = it }
      }
    }
  }
}

// ---- Images ----

@Composable
private fun ImagesPage() {
  PageList {
    item { MsHeader("Album art") }
    item {
      MsChoice("Cover shape", listOf("Rounded", "Circle", "Square"), MusicSettings.coverShape) { msSave { MusicSettings.coverShape = it } }
    }
    item { MsSwitch("Show album art in song lists", MusicSettings.showSongArt) { msSave { MusicSettings.showSongArt = it } } }
    item {
      MsChoice("Album grid columns", listOf("2", "3", "4"), MusicSettings.gridColumns - 2) { msSave { MusicSettings.gridColumns = it + 2 } }
    }
    item {
      MsNote("Covers come from the artwork saved in your music files. Downloading artist pictures from the internet is not supported.")
    }
  }
}

// ---- Other ----

@Composable
private fun OtherPage(
  onScan: () -> Unit,
  onClearHistory: () -> Unit,
) {
  PageList {
    item { MsHeader("Library") }
    item { MsAction("Scan phone for new music", "Look for songs added recently", onScan) }
    item { MsAction("Clear history", "Remove the recently played list", onClearHistory) }
    item { MsHeader("Reset") }
    item { MsAction("Reset all music settings", "Go back to the default options") { MusicSettings.reset() } }
  }
}

// ---- Backup & Restore ----

private fun buildBackup(): JSONObject =
  JSONObject()
    .put("app", "one-player-music")
    .put("version", 1)
    .put("settings", MusicSettings.toJson())
    .put("library", MusicController.exportJson())

@Composable
private fun BackupPage() {
  val context = LocalContext.current
  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
      if (uri != null) {
        val ok =
          runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(buildBackup().toString(2).toByteArray()) }
              ?: error("cannot write")
          }.isSuccess
        Toast.makeText(context, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show()
      }
    }
  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        val ok =
          runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("cannot read")
            val o = JSONObject(text)
            o.optJSONObject("settings")?.let { MusicSettings.applyJson(it) }
            o.optJSONObject("library")?.let { MusicController.importJson(it) }
          }.isSuccess
        Toast.makeText(context, if (ok) "Backup restored" else "This file is not a music backup", Toast.LENGTH_SHORT).show()
      }
    }
  PageList {
    item { MsHeader("Backup") }
    item {
      MsAction("Back up now", "Save settings, favorites, history and playlists to a file") {
        exportLauncher.launch("one-player-music-backup.json")
      }
    }
    item {
      MsAction("Restore from file", "Load a backup file you saved before") {
        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
      }
    }
    item { MsNote("Playlists and favorites remember songs by their number on this phone, so restore them on the same phone.") }
  }
}

// ---- About ----

@Composable
private fun AboutPage() {
  val context = LocalContext.current
  val version =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
  PageList {
    item { MsHeader("Music player") }
    item { MsAction("Version", version) {} }
    item { MsAction("Package", context.packageName) {} }
    item { MsNote("A Retro Music Player style music screen built into this app.") }
  }
}

// ---------------------------------------------------------------------------------------------
// Row building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun MsHeader(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
  )
}

@Composable
private fun MsNote(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.outline,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  )
}

@Composable
private fun MsChoice(
  title: String,
  options: List<String>,
  selected: Int,
  onSelect: (Int) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      options.forEachIndexed { i, label ->
        FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) })
      }
    }
  }
}

@Composable
private fun MsSwitch(
  title: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

@Composable
private fun MsAction(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
  }
}

@Composable
private fun MsSlider(
  title: String,
  value: Int,
  range: IntRange,
  suffix: String,
  note: String,
  onChange: (Int) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
      Text("$value$suffix", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
    Slider(
      value = value.toFloat(),
      onValueChange = { onChange(it.toInt()) },
      valueRange = range.first.toFloat()..range.last.toFloat(),
      onValueChangeFinished = { MusicSettings.save() },
    )
    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
  }
}
