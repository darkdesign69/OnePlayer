package app.marlboroadvance.mpvex.ui.preferences

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.music.MusicSettings
import app.marlboroadvance.mpvex.ui.browser.music.buildFontFamily
import app.marlboroadvance.mpvex.ui.browser.music.builtInFontNames
import app.marlboroadvance.mpvex.ui.browser.music.importCustomFont
import app.marlboroadvance.mpvex.ui.browser.music.removeCustomFont
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable

private fun saveFont(block: () -> Unit) {
  block()
  MusicSettings.save()
}

/** Font family, custom font, size, bold and italic. Applied to the whole app (video player and music player). */
@Serializable
object FontSettingsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    MusicSettings.init(context)
    val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
          val name = importCustomFont(context, uri)
          Toast
            .makeText(
              context,
              if (name != null) "Font added: $name" else "That is not a valid font. Please choose a .ttf or .otf file.",
              Toast.LENGTH_LONG,
            ).show()
        }
      }
    val hasCustom = MusicSettings.customFontPath.isNotEmpty()
    val customFamily = remember(MusicSettings.customFontPath) { buildFontFamily(5, MusicSettings.customFontPath, false, false) }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Font",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            }
          },
        )
      },
    ) { padding ->
      LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        item { PreferenceSectionHeader(title = "Font family") }
        item {
          PreferenceCard {
            builtInFontNames.forEachIndexed { i, label ->
              if (i > 0) PreferenceDivider()
              val family = remember(i) { buildFontFamily(i, "", false, false) }
              FontRow(label, "The quick brown fox jumps over the lazy dog", family, MusicSettings.fontChoice == i) {
                saveFont { MusicSettings.fontChoice = i }
              }
            }
          }
        }
        item { PreferenceSectionHeader(title = "Custom font") }
        item {
          PreferenceCard {
            FontRow(
              label = "Use custom font",
              sample = if (hasCustom) MusicSettings.customFontName else "No font added yet",
              family = customFamily,
              selected = MusicSettings.fontChoice == 5,
              enabled = hasCustom,
            ) { saveFont { MusicSettings.fontChoice = 5 } }
            PreferenceDivider()
            TextRow("Add your own font", "Choose a .ttf or .otf font file from your phone") { picker.launch(arrayOf("*/*")) }
            if (hasCustom) {
              PreferenceDivider()
              TextRow("Remove custom font", MusicSettings.customFontName) { removeCustomFont(context) }
            }
          }
        }
        item {
          Text(
            "TTF and OTF are the standard font formats that work on every phone and computer. The font is used in the whole app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
          )
        }
        item { PreferenceSectionHeader(title = "Size and style") }
        item {
          PreferenceCard {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Text size", style = MaterialTheme.typography.titleMedium)
                  Text("${MusicSettings.fontScale}%  (100 is the normal size)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
                TextButton(onClick = { saveFont { MusicSettings.fontScale = 100 } }) { Text("Reset") }
              }
              Slider(
                value = MusicSettings.fontScale.toFloat(),
                onValueChange = { saveFont { MusicSettings.fontScale = it.toInt() } },
                valueRange = 70f..160f,
              )
            }
            PreferenceDivider()
            SwitchRow("Bold", "Make all text bold", MusicSettings.fontBold) { saveFont { MusicSettings.fontBold = it } }
            PreferenceDivider()
            SwitchRow("Italic", "Make all text italic", MusicSettings.fontItalic) { saveFont { MusicSettings.fontItalic = it } }
          }
        }
      }
    }
  }
}

@Composable
private fun FontRow(
  label: String,
  sample: String,
  family: FontFamily?,
  selected: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = family,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
      )
      Text(sample, style = MaterialTheme.typography.bodyMedium, fontFamily = family, color = MaterialTheme.colorScheme.outline)
    }
    RadioButton(selected = selected, onClick = null, enabled = enabled)
  }
}

@Composable
private fun TextRow(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
  }
}

@Composable
private fun SwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
