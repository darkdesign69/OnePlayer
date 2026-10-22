package app.marlboroadvance.mpvex.ui.browser.music

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

// ---------------------------------------------------------------------------------------------
// Colours
// ---------------------------------------------------------------------------------------------

internal val accentPalette: List<Color> =
  listOf(
    0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFFCDDC39, 0xFFFFC107,
    0xFFFF9800, 0xFFFF5722, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
  ).map { Color(it) }

internal val themeSeeds: List<Color> =
  listOf(
    0xFF9CC2FF, 0xFF7ED3E8, 0xFF8ED8B0, 0xFFE6C86B, 0xFFF2A07B, 0xFFF29DB8, 0xFFC7A8F0, 0xFFB4BCC8,
  ).map { Color(it) }

private fun Color.withMinAlpha(a: Float): Color = if (alpha < a) copy(alpha = a) else this

private fun tinted(
  cs: ColorScheme,
  seed: Color,
  setContainers: Boolean,
): ColorScheme {
  val base = cs.surface.copy(alpha = 1f)
  val onSeed = if (seed.luminance() > 0.5f) Color.Black else Color.White
  return if (setContainers) {
    cs.copy(
      primary = seed,
      onPrimary = onSeed,
      primaryContainer = lerp(base, seed, 0.30f),
      onPrimaryContainer = cs.onSurface,
      secondaryContainer = lerp(base, seed, 0.22f),
      onSecondaryContainer = cs.onSurface,
      surfaceTint = seed,
    )
  } else {
    cs.copy(primary = seed, onPrimary = onSeed, surfaceTint = seed)
  }
}

/**
 * Applies the music theme options (light/dark, Material You, colour theme, extra dark, accent) and keeps
 * cards, bars and panels visible when the app uses a see-through custom theme.
 */
@Composable
private fun MusicColorTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val sysDark = isSystemInDarkTheme()
  val appCs = MaterialTheme.colorScheme
  var cs: ColorScheme =
    when {
      MusicSettings.materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val dark =
          when (MusicSettings.themeMode) {
            1 -> false
            2 -> true
            else -> sysDark
          }
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      MusicSettings.themeMode == 1 -> lightColorScheme()
      MusicSettings.themeMode == 2 -> darkColorScheme()
      else -> appCs
    }
  val isDark = cs.onSurface.luminance() > 0.5f
  if (MusicSettings.extraDark && isDark) {
    cs =
      cs.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF080808),
        surfaceContainer = Color(0xFF0D0D0D),
        surfaceContainerHigh = Color(0xFF141414),
        surfaceContainerHighest = Color(0xFF1C1C1C),
      )
  }
  val seed = themeSeeds.getOrNull(MusicSettings.themeSeed - 1)
  if (seed != null && !(MusicSettings.materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) cs = tinted(cs, seed, true)
  val accent = accentPalette.getOrNull(MusicSettings.accentIdx - 1)
  if (accent != null) cs = tinted(cs, accent, false)

  if (cs.surface.alpha >= 0.99f) {
    MaterialTheme(colorScheme = cs, content = content)
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

/** Colours, font, size, bold and italic of the whole player. */
@Composable
internal fun MusicPanelTheme(content: @Composable () -> Unit) {
  MusicColorTheme { MusicTypographyTheme(content) }
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

/**
 * Keeps a part of the screen (Now playing, the mini player, the lyrics editor) looking the same whatever the
 * app theme does: every background inside it is fully solid, so custom theme transparency can't change it.
 */
@Composable
internal fun SolidThemed(content: @Composable () -> Unit) {
  val cs = MaterialTheme.colorScheme
  val base = solidSurface()
  fun fix(
    c: Color,
    fallback: Color,
  ): Color = if (c.alpha >= 0.99f) c else fallback
  val container = lerp(base, cs.onSurface.copy(alpha = 1f), 0.06f)
  val containerHigh = lerp(base, cs.onSurface.copy(alpha = 1f), 0.10f)
  val accentContainer = lerp(base, cs.primary.copy(alpha = 1f), 0.30f)
  val fixed =
    cs.copy(
      background = fix(cs.background, base),
      surface = fix(cs.surface, base),
      surfaceVariant = fix(cs.surfaceVariant, containerHigh),
      surfaceDim = fix(cs.surfaceDim, base),
      surfaceBright = fix(cs.surfaceBright, containerHigh),
      surfaceContainerLowest = fix(cs.surfaceContainerLowest, base),
      surfaceContainerLow = fix(cs.surfaceContainerLow, container),
      surfaceContainer = fix(cs.surfaceContainer, container),
      surfaceContainerHigh = fix(cs.surfaceContainerHigh, containerHigh),
      surfaceContainerHighest = fix(cs.surfaceContainerHighest, containerHigh),
      primaryContainer = fix(cs.primaryContainer, accentContainer),
      secondaryContainer = fix(cs.secondaryContainer, accentContainer),
      tertiaryContainer = fix(cs.tertiaryContainer, accentContainer),
      errorContainer = fix(cs.errorContainer, containerHigh),
      inverseSurface = fix(cs.inverseSurface, containerHigh),
    )
  MaterialTheme(colorScheme = fixed, content = content)
}

/** Background for menus, dialogs and sheets: always solid so the text stays readable. */
@Composable
internal fun popupColor(): Color {
  val cs = MaterialTheme.colorScheme
  val c = cs.surfaceContainerHigh
  if (c.alpha >= 0.99f) return c
  return lerp(solidSurface(), cs.primary, 0.10f)
}

// ---------------------------------------------------------------------------------------------
// Hub + host
// ---------------------------------------------------------------------------------------------

internal val musicSettingsTitles = listOf("Themes & Colors", "Now playing", "Library", "Images", "Audio", "Playlists")

private class HubItem(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
)

private val hubItems =
  listOf(
    HubItem("Themes & Colors", "Change theme, accent color and highlight color", Icons.Filled.Palette),
    HubItem("Now playing", "Themes, extra controls, display content, lockscreen", Icons.Filled.PlayArrow),
    HubItem("Library", "Multiple artists, filters, navigation", Icons.Filled.Category),
    HubItem("Images", "Image caching, artwork, lockscreen art", Icons.Filled.Image),
    HubItem("Audio", "Fade audio, focus, auto play and more", Icons.Filled.MusicNote),
    HubItem("Playlists", "Last added interval, backup, import and export", Icons.AutoMirrored.Filled.QueueMusic),
  )

private fun upd(block: () -> Unit) {
  block()
  MusicSettings.save()
}

@Composable
internal fun MusicSettingsHost(
  page: Int,
  onPage: (Int) -> Unit,
  tracks: List<MusicTrack>,
  onScan: () -> Unit,
  onClearHistory: () -> Unit,
) {
  when (page) {
    0 -> ThemesPage()
    1 -> NowPlayingPage()
    2 -> LibraryPage(tracks, onScan, onClearHistory)
    3 -> ImagesPage()
    4 -> AudioPage()
    5 -> PlaylistsPage()
    else -> SettingsHub(onPage)
  }
}

@Composable
private fun SettingsHub(onPage: (Int) -> Unit) {
  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
    item { PageTitle("Settings") }
    item {
      Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      ) {
        Column {
          hubItems.forEachIndexed { i, h ->
            Row(
              modifier = Modifier.fillMaxWidth().clickable { onPage(i) }.padding(horizontal = 24.dp, vertical = 26.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
              Icon(h.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.titleLarge)
                Text(h.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PageTitle(text: String) {
  Text(text, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 24.dp, top = 40.dp, bottom = 28.dp))
}

@Composable
private fun OtoPage(
  title: String,
  content: LazyListScope.() -> Unit,
) {
  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
    item { PageTitle(title) }
    content()
  }
}

// ---------------------------------------------------------------------------------------------
// Themes & Colors
// ---------------------------------------------------------------------------------------------

@Composable
private fun ThemesPage() {
  var accentDialog by remember { mutableStateOf(false) }
  var highlightDialog by remember { mutableStateOf(false) }
  OtoPage("Themes & Colors") {
    item {
      Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
        Row(modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE2E2E6)).padding(6.dp)) {
          listOf("Light" to 1, "Dark" to 2, "System" to 0).forEach { (label, mode) ->
            val sel = MusicSettings.themeMode == mode
            Box(
              modifier =
                Modifier
                  .clip(RoundedCornerShape(50))
                  .background(if (sel) Color.Black else Color.Transparent)
                  .clickable { upd { MusicSettings.themeMode = mode } }
                  .padding(horizontal = 26.dp, vertical = 16.dp),
            ) { Text(label, color = if (sel) Color.White else Color.Black, fontWeight = MusicFont.medium) }
          }
        }
      }
    }
    item {
      OtoSwitch("Material You themes", "Use your phone's wallpaper colors (Android 12 or newer).", MusicSettings.materialYou, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        upd { MusicSettings.materialYou = it }
      }
    }
    item {
      LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 12.dp)) {
        itemsIndexed((0..8).toList()) { _, idx ->
          val seed = themeSeeds.getOrNull(idx - 1) ?: MaterialTheme.colorScheme.primary
          val sel = MusicSettings.themeSeed == idx
          Box(
            modifier =
              Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF0A1018))
                .then(if (sel) Modifier.border(3.dp, seed, RoundedCornerShape(26.dp)) else Modifier)
                .clickable { upd { MusicSettings.themeSeed = idx } },
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.size(76.dp)) {
              val bottom = lerp(seed, Color(0xFFD7A8E0), 0.6f)
              drawArc(seed, 180f, 180f, true, Offset.Zero, Size(size.width, size.height))
              drawArc(bottom, 0f, 180f, true, Offset.Zero, Size(size.width, size.height))
              drawCircle(Color(0xFF0A2540), radius = size.minDimension * 0.31f)
            }
          }
        }
      }
    }
    item {
      OtoSwitch("Use extra dark colors", "Use darker colors for small battery savings on AMOLED screens.", MusicSettings.extraDark) {
        upd { MusicSettings.extraDark = it }
      }
    }
    item {
      OtoRow("Accent color", "Used to emphasize select parts of the UI.", true, { accentDialog = true }) {
        ColorDot(accentPalette.getOrNull(MusicSettings.accentIdx - 1) ?: MaterialTheme.colorScheme.primary)
      }
    }
    item {
      OtoRow("Highlight color", "Used to highlight the current playing song.", true, { highlightDialog = true }) {
        ColorDot(accentPalette.getOrNull(MusicSettings.highlightIdx - 1) ?: MaterialTheme.colorScheme.primary)
      }
    }
    item {
      OtoSlider("Highlight color alpha", "Set highlight color transparency", MusicSettings.highlightAlpha, 0..100) { upd { MusicSettings.highlightAlpha = it } }
    }
    item {
      OtoSlider(
        "Card & bar opacity",
        "Keeps cards, bars and panels visible when your custom app theme is very transparent",
        MusicSettings.panelOpacity,
        0..100,
      ) { upd { MusicSettings.panelOpacity = it } }
    }
  }
  if (accentDialog) ColorDialog("Accent color", MusicSettings.accentIdx, { upd { MusicSettings.accentIdx = it } }) { accentDialog = false }
  if (highlightDialog) ColorDialog("Highlight color", MusicSettings.highlightIdx, { upd { MusicSettings.highlightIdx = it } }) { highlightDialog = false }
}

// ---------------------------------------------------------------------------------------------
// Now playing
// ---------------------------------------------------------------------------------------------

@Composable
private fun NowPlayingPage() {
  OtoPage("Now playing") {
    item {
      OtoChoice("Now Playing Theme", listOf("Default", "Colored", "Gradient", "Blur", "Gradient Blur", "Glow", "Material You"), MusicSettings.nowPlayingBg) {
        upd { MusicSettings.nowPlayingBg = it }
      }
    }
    if (MusicSettings.nowPlayingBg == 3 || MusicSettings.nowPlayingBg == 4) {
      item { OtoSlider("Blur amount", "Needs Android 12 or newer to blur", MusicSettings.blurAmount, 5..100) { upd { MusicSettings.blurAmount = it } } }
    }
    item {
      OtoSwitch("Show song info", "Show song information such as file format, bitrate and frequency in the main player", MusicSettings.songInfo) {
        upd { MusicSettings.songInfo = it }
      }
    }
    item {
      OtoSwitch("Extra controls", "Add previous and forward button in the mini player", MusicSettings.miniControls == 2) {
        upd { MusicSettings.miniControls = if (it) 2 else 0 }
      }
    }
    item {
      OtoSwitch("Show remaining time", "Show remaining time instead of duration in the main player", MusicSettings.remainingTime) {
        upd { MusicSettings.remainingTime = it }
      }
    }
    item {
      OtoSwitch("Show volume slider", "Show volume slider in the main player", MusicSettings.volumeSlider) { upd { MusicSettings.volumeSlider = it } }
    }
    item { OtoHeader("Lyrics") }
    item {
      OtoSwitch("Search lyrics online", "Find and save lyrics from the web when a song has none", MusicSettings.lyricsAuto) { upd { MusicSettings.lyricsAuto = it } }
    }
    item { OtoHeader("More") }
    item { OtoChoice("Cover size", listOf("Large", "Medium", "Small"), MusicSettings.coverSize) { upd { MusicSettings.coverSize = it } } }
    item { OtoSwitch("Swipe cover to change song", null, MusicSettings.swipeCover) { upd { MusicSettings.swipeCover = it } } }
    item { OtoSwitch("Show favorite button", null, MusicSettings.showFavButton) { upd { MusicSettings.showFavButton = it } } }
    item { OtoSwitch("Show sleep timer and queue buttons", null, MusicSettings.showExtraControls) { upd { MusicSettings.showExtraControls = it } } }
    item { OtoSwitch("Open Now playing when a song starts", "Off: Now playing only opens when you tap the mini player", MusicSettings.openNowOnPlay) { upd { MusicSettings.openNowOnPlay = it } } }
    item { OtoSwitch("Keep screen on", "While Now playing is open", MusicSettings.keepScreenOn) { upd { MusicSettings.keepScreenOn = it } } }
  }
}

// ---------------------------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------------------------

private val tabNames = listOf("Home", "Songs", "Albums", "Artists", "Playlists", "Folders")

@Composable
private fun LibraryPage(
  tracks: List<MusicTrack>,
  onScan: () -> Unit,
  onClearHistory: () -> Unit,
) {
  var tabsDialog by remember { mutableStateOf(false) }
  var blacklistDialog by remember { mutableStateOf(false) }
  var symbolsDialog by remember { mutableStateOf(false) }
  OtoPage("Library") {
    item { OtoRow("Manage bottom tabs", "Reorder and set visibility of bottom tabs", true, { tabsDialog = true }) }
    item { OtoSwitch("Show See all button", "Show or hide See all button in details page.", MusicSettings.seeAll) { upd { MusicSettings.seeAll = it } } }
    item { OtoHeader("Multiple artists") }
    item {
      OtoSwitch("Multiple artists support", "Split artist tags with (, & ;) separators.", MusicSettings.multiArtists) { upd { MusicSettings.multiArtists = it } }
    }
    item {
      OtoRow("Symbols to split artists", "Add separators based on which artists will be split (now: ${MusicSettings.artistSeparators})", MusicSettings.multiArtists, { symbolsDialog = true })
    }
    item { OtoHeader("Filter") }
    item {
      OtoRow("Blacklist", "Hide folders from the library (${MusicSettings.excluded().size} hidden)", true, { blacklistDialog = true })
    }
    item {
      val values = listOf(0, 10, 30, 60)
      OtoChoice("Hide songs shorter than", listOf("Off", "10 sec", "30 sec", "60 sec"), values.indexOf(MusicSettings.minDurationSec).coerceAtLeast(0)) {
        upd { MusicSettings.minDurationSec = values[it] }
      }
    }
    item { OtoHeader("Navigation") }
    item { OtoChoice("Open music on", listOf("Last used tab", "Fixed tab"), MusicSettings.openMode) { upd { MusicSettings.openMode = it } } }
    if (MusicSettings.openMode == 1) {
      item { OtoChoice("Fixed tab", tabNames, MusicSettings.defaultTab.coerceIn(0, 5)) { upd { MusicSettings.defaultTab = it } } }
    }
    item { OtoChoice("Tab labels", listOf("Always", "Selected only", "Hidden"), MusicSettings.tabLabels) { upd { MusicSettings.tabLabels = it } } }
    item { OtoSwitch("Show song duration", "In song lists", MusicSettings.showSongDuration) { upd { MusicSettings.showSongDuration = it } } }
    item { OtoHeader("Home page") }
    item { OtoSwitch("Recently played", null, MusicSettings.homeRecent) { upd { MusicSettings.homeRecent = it } } }
    item { OtoSwitch("Last added", null, MusicSettings.homeLastAdded) { upd { MusicSettings.homeLastAdded = it } } }
    item { OtoSwitch("Albums", null, MusicSettings.homeAlbums) { upd { MusicSettings.homeAlbums = it } } }
    item { OtoHeader("Library tools") }
    item { OtoRow("Scan phone for new music", "Look for songs added recently", true, onScan) }
    item { OtoRow("Clear history", "Remove the recently played list", true, onClearHistory) }
    item { OtoRow("Reset all music settings", "Go back to the default options", true, { MusicSettings.reset() }) }
  }
  if (tabsDialog) TabsDialog { tabsDialog = false }
  if (blacklistDialog) BlacklistDialog(tracks) { blacklistDialog = false }
  if (symbolsDialog) {
    var text by remember { mutableStateOf(MusicSettings.artistSeparators) }
    AlertDialog(
      containerColor = popupColor(),
      onDismissRequest = { symbolsDialog = false },
      title = { Text("Symbols to split artists") },
      text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("For example , & ;") }) },
      confirmButton = {
        TextButton(onClick = { upd { MusicSettings.artistSeparators = text.replace(" ", "").ifEmpty { ",&;" } }; symbolsDialog = false }) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { symbolsDialog = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun TabsDialog(onDismiss: () -> Unit) {
  val order = remember { mutableStateListOf<Int>().apply { addAll(MusicSettings.tabOrderList()) } }
  var mask by remember { mutableIntStateOf(MusicSettings.tabsMask) }
  AlertDialog(
    containerColor = popupColor(),
    onDismissRequest = onDismiss,
    title = { Text("Manage bottom tabs") },
    text = {
      Column {
        order.forEachIndexed { pos, tab ->
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
              checked = (mask and (1 shl tab)) != 0,
              onCheckedChange = {
                val next = if (it) mask or (1 shl tab) else mask and (1 shl tab).inv()
                if (next and 63 != 0) mask = next and 63
              },
            )
            Text(tabNames[tab], modifier = Modifier.weight(1f))
            IconButton(enabled = pos > 0, onClick = { order.add(pos - 1, order.removeAt(pos)) }) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
            IconButton(enabled = pos < order.lastIndex, onClick = { order.add(pos + 1, order.removeAt(pos)) }) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          upd {
            MusicSettings.tabOrder = order.joinToString(",")
            MusicSettings.tabsMask = mask
          }
          onDismiss()
        },
      ) { Text("Save") }
    },
    dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun BlacklistDialog(
  tracks: List<MusicTrack>,
  onDismiss: () -> Unit,
) {
  val excluded = MusicSettings.excluded()
  val folders = (tracks.mapNotNull { t -> t.path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() } } + excluded).distinct().sorted()
  AlertDialog(
    containerColor = popupColor(),
    onDismissRequest = onDismiss,
    title = { Text("Blacklist") },
    text = {
      if (folders.isEmpty()) {
        Text("No folders found")
      } else {
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
          itemsIndexed(folders) { _, f ->
            val hidden = f in excluded
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier.fillMaxWidth().clickable {
                  val next = if (hidden) excluded - f else excluded + f
                  upd { MusicSettings.excludedFolders = next.joinToString("|") }
                },
            ) {
              Checkbox(checked = hidden, onCheckedChange = null)
              Spacer(Modifier.width(8.dp))
              Text(f, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp))
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}

// ---------------------------------------------------------------------------------------------
// Images
// ---------------------------------------------------------------------------------------------

@Composable
private fun ImagesPage() {
  OtoPage("Images") {
    item { OtoSwitch("Cache images", "Faster loading of images but will increase app size due to caching.", MusicSettings.cacheImages) { upd { MusicSettings.cacheImages = it } } }
    item { OtoHeader("Metadata") }
    item {
      OtoSwitch("Use MediaStore provided artworks", "Faster artwork loading time, but can show incorrect artwork", MusicSettings.useMediaStoreArt) {
        upd { MusicSettings.useMediaStoreArt = it }
      }
    }
    item { OtoHeader("Lockscreen") }
    item { OtoSwitch("Lockscreen album art", "Show album art on the lockscreen", MusicSettings.lockArt) { upd { MusicSettings.lockArt = it } } }
    item { OtoSwitch("Blur album cover", "Blur album art on the lockscreen", MusicSettings.lockBlur, MusicSettings.lockArt) { upd { MusicSettings.lockBlur = it } } }
    item { OtoHeader("Appearance") }
    item { OtoChoice("Cover shape", listOf("Rounded", "Circle", "Square"), MusicSettings.coverShape) { upd { MusicSettings.coverShape = it } } }
    item { OtoSwitch("Show album art in song lists", null, MusicSettings.showSongArt) { upd { MusicSettings.showSongArt = it } } }
    item { OtoChoice("Album grid columns", listOf("2", "3", "4"), MusicSettings.gridColumns - 2) { upd { MusicSettings.gridColumns = it + 2 } } }
  }
}

// ---------------------------------------------------------------------------------------------
// Audio
// ---------------------------------------------------------------------------------------------

@Composable
private fun AudioPage() {
  val context = LocalContext.current
  OtoPage("Audio") {
    item { OtoSwitch("Fade music on play or pause (Beta)", "Smooth fade music on pause or resume playback.", MusicSettings.audioFade) { upd { MusicSettings.audioFade = it } } }
    item { OtoSwitch("Lower volume on focus loss", "Lower the volume when a system sound is played or a notification is received", MusicSettings.duck) { upd { MusicSettings.duck = it } } }
    item { OtoSwitch("Auto play", "Start playing immediately when a wired headset or bluetooth audio device is plugged in.", MusicSettings.autoPlay) { upd { MusicSettings.autoPlay = it } } }
    item { OtoSwitch("Keep shuffle mode", "Shuffle mode won't turn off when playing a new list of songs.", MusicSettings.keepShuffle) { upd { MusicSettings.keepShuffle = it } } }
    item { OtoSwitch("Pause when headphones disconnect", null, MusicSettings.pauseOnUnplug) { upd { MusicSettings.pauseOnUnplug = it } } }
    item { OtoSwitch("Remember queue and position", "Continue where you stopped next time", MusicSettings.restoreQueue) { upd { MusicSettings.restoreQueue = it } } }
    item { OtoRow("Equalizer", "Open the equalizer of your phone", true, { openEqualizer(context) }) }
  }
}


// ---------------------------------------------------------------------------------------------
// Playlists (+ backup)
// ---------------------------------------------------------------------------------------------

private fun buildBackup(): JSONObject =
  JSONObject()
    .put("app", "one-player-music")
    .put("version", 1)
    .put("settings", MusicSettings.toJson())
    .put("library", MusicController.exportJson())

@Composable
private fun PlaylistsPage() {
  val context = LocalContext.current
  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
      if (uri != null) {
        val ok =
          runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(buildBackup().toString(2).toByteArray()) } ?: error("cannot write")
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
  OtoPage("Playlists") {
    item {
      val labels = listOf("No limit", "Past day", "Past week", "Past month", "Past three months", "Past six months", "Past year")
      val days = listOf(0, 1, 7, 30, 90, 180, 365)
      OtoChoice("Last added playlist interval", labels, days.indexOf(MusicSettings.lastAddedDays).coerceAtLeast(0)) { upd { MusicSettings.lastAddedDays = days[it] } }
    }
    item { OtoHeader("Import and export") }
    item { OtoRow("Back up now", "Save settings, favorites, history and playlists to a file", true, { exportLauncher.launch("one-player-music-backup.json") }) }
    item {
      OtoRow("Restore from file", "Load a backup file you saved before", true, {
        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
      })
    }
    item { OtoNote("Playlists and favorites remember songs by their number on this phone, so restore them on the same phone.") }
  }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun OtoHeader(text: String) {
  Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 4.dp),
  )
}

@Composable
private fun OtoNote(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
private fun OtoRow(
  title: String,
  subtitle: String?,
  enabled: Boolean = true,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
  val subColor = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subColor)
    }
    if (trailing != null) {
      Spacer(Modifier.width(16.dp))
      trailing()
    }
  }
}

@Composable
private fun OtoSwitch(
  title: String,
  subtitle: String?,
  checked: Boolean,
  enabled: Boolean = true,
  onChange: (Boolean) -> Unit,
) {
  OtoRow(title, subtitle, enabled, { onChange(!checked) }) { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) }
}

@Composable
private fun OtoChoice(
  title: String,
  options: List<String>,
  selected: Int,
  onSelect: (Int) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  OtoRow(title, options.getOrElse(selected) { options.first() }, true, { open = true })
  if (open) {
    AlertDialog(
      containerColor = popupColor(),
      onDismissRequest = { open = false },
      title = { Text(title) },
      text = {
        Column {
          options.forEachIndexed { i, label ->
            Row(
              modifier = Modifier.fillMaxWidth().clickable { onSelect(i); open = false }.padding(vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
              RadioButton(selected = i == selected, onClick = null)
            }
          }
        }
      },
      confirmButton = {},
    )
  }
}

@Composable
private fun OtoSlider(
  title: String,
  subtitle: String,
  value: Int,
  range: IntRange,
  onChange: (Int) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      Text("$value", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat())
  }
}

@Composable
private fun ColorDot(color: Color) {
  Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(color))
}

@Composable
private fun ColorDialog(
  title: String,
  selected: Int,
  onSelect: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    containerColor = popupColor(),
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        accentPalette.chunked(5).forEachIndexed { row, colors ->
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEachIndexed { col, c ->
              val idx = row * 5 + col + 1
              Box(
                modifier =
                  Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(if (selected == idx) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(idx); onDismiss() },
              )
            }
          }
        }
        TextButton(onClick = { onSelect(0); onDismiss() }) { Text("Use default") }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}
