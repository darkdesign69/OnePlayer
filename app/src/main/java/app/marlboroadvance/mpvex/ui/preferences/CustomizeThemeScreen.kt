package app.marlboroadvance.mpvex.ui.preferences

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.theme.AccentColors
import app.marlboroadvance.mpvex.ui.theme.BackgroundPresets
import app.marlboroadvance.mpvex.ui.theme.CUSTOM_BG_IMAGE
import app.marlboroadvance.mpvex.ui.theme.CUSTOM_BG_NONE
import app.marlboroadvance.mpvex.ui.theme.CustomBackgroundLayer
import app.marlboroadvance.mpvex.ui.theme.backgroundImageFile
import app.marlboroadvance.mpvex.ui.theme.presetMode
import app.marlboroadvance.mpvex.ui.theme.rememberBackgroundBitmap
import app.marlboroadvance.mpvex.ui.theme.saveBackgroundImage
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/**
 * Customize Theme: choose a background picture / colour, an accent colour, and how
 * transparent the panels are and how blurred the picture is.
 */
@Serializable
object CustomizeThemeScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<AppearancePreferences>()
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showColorPicker by remember { mutableStateOf(false) }

    val mode by preferences.customBackground.collectAsState()
    val version by preferences.customBackgroundVersion.collectAsState()
    val transparency by preferences.customBackgroundTransparency.collectAsState()
    val blur by preferences.customBackgroundBlur.collectAsState()
    val dim by preferences.customBackgroundDim.collectAsState()
    val accent by preferences.customAccent.collectAsState()

    val pickPhoto =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
          scope.launch {
            if (saveBackgroundImage(context, uri)) {
              preferences.customBackgroundVersion.set(preferences.customBackgroundVersion.get() + 1)
              preferences.customBackground.set(CUSTOM_BG_IMAGE)
            } else {
              Toast.makeText(context, "Couldn't load that picture", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
    val launchPicker = {
      pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (showColorPicker) {
      ColorPickerDialog(
        initial = if (accent != 0) Color(accent) else MaterialTheme.colorScheme.primary,
        onDismiss = { showColorPicker = false },
        onPick = { picked ->
          preferences.customAccent.set(picked.toArgb())
          showColorPicker = false
        },
      )
    }

    val hasSavedPhoto = backgroundImageFile(context).exists()
    val backgroundShown = mode != CUSTOM_BG_NONE

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Customize Theme",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // ---------- Live preview ----------
        Box(
          modifier =
            Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxHeight()
                .aspectRatio(0.5f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
          ) {
            if (backgroundShown) {
              CustomBackgroundLayer(
                mode = mode,
                version = version,
                blur = blur,
                dim = dim,
                modifier = Modifier.fillMaxSize(),
              )
            }
            Column(
              modifier = Modifier.fillMaxSize().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "OnlyPlay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              repeat(5) { index ->
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .height(44.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(MaterialTheme.colorScheme.surfaceContainer)
                      .padding(horizontal = 12.dp),
                  contentAlignment = Alignment.CenterStart,
                ) {
                  Text(
                    text = "Sample video ${index + 1}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }
            }
          }
        }

        // ---------- Controls ----------
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            // Background choices
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              ThemeTile(
                selected = mode == CUSTOM_BG_NONE,
                onClick = { preferences.customBackground.set(CUSTOM_BG_NONE) },
              ) {
                Icon(
                  Icons.Outlined.Block,
                  contentDescription = "No background",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              ThemeTile(
                selected = mode == CUSTOM_BG_IMAGE,
                onClick = {
                  if (hasSavedPhoto) {
                    preferences.customBackground.set(CUSTOM_BG_IMAGE)
                  } else {
                    launchPicker()
                  }
                },
              ) {
                val thumbnail = rememberBackgroundBitmap(CUSTOM_BG_IMAGE, version)
                if (thumbnail != null && hasSavedPhoto) {
                  Image(
                    bitmap = thumbnail,
                    contentDescription = "Your picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = "Choose a picture",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              BackgroundPresets.forEach { preset ->
                ThemeTile(
                  selected = mode == presetMode(preset.id),
                  onClick = { preferences.customBackground.set(presetMode(preset.id)) },
                ) {
                  Box(modifier = Modifier.fillMaxSize().background(preset.brush()))
                }
              }
            }

            // Accent colour
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = "Color",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                AccentDot(
                  color = null,
                  selected = accent == 0,
                  onClick = { preferences.customAccent.set(0) },
                )
                AccentColors.forEach { color ->
                  AccentDot(
                    color = color,
                    selected = accent == color.toArgb(),
                    onClick = { preferences.customAccent.set(color.toArgb()) },
                  )
                }
                CustomColorDot(
                  selected = accent != 0 && AccentColors.none { it.toArgb() == accent },
                  onClick = { showColorPicker = true },
                )
              }
            }

            if (backgroundShown) {
              LabeledSlider(
                label = "Transparency",
                value = transparency,
                onValueChange = { preferences.customBackgroundTransparency.set(it) },
              )
            }
            if (mode == CUSTOM_BG_IMAGE) {
              LabeledSlider(
                label = "Blur",
                value = blur,
                onValueChange = { preferences.customBackgroundBlur.set(it) },
              )
            }
            if (backgroundShown) {
              LabeledSlider(
                label = "Dark overlay",
                value = dim,
                onValueChange = { preferences.customBackgroundDim.set(it) },
              )
            }

            Button(
              onClick = { launchPicker() },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(text = if (hasSavedPhoto) "Change picture" else "Select picture")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ThemeTile(
  selected: Boolean,
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(14.dp)
  Box(
    modifier =
      Modifier
        .size(56.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(
          width = if (selected) 2.dp else 1.dp,
          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          shape = shape,
        ).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    content()
    if (selected) {
      Box(
        modifier =
          Modifier
            .align(Alignment.BottomEnd)
            .padding(3.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.Check,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(12.dp),
        )
      }
    }
  }
}

@Composable
private fun AccentDot(
  color: Color?,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
        .border(
          width = if (selected) 2.dp else 1.dp,
          color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
          shape = CircleShape,
        ).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (color == null) {
      Icon(
        Icons.Outlined.Block,
        contentDescription = "Default color",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    } else if (selected) {
      Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = if (color.luminanceIsHigh()) Color.Black else Color.White,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

private fun Color.luminanceIsHigh(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) > 0.6f

@Composable
private fun LabeledSlider(
  label: String,
  value: Float,
  onValueChange: (Float) -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(end = 4.dp),
    )
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = 0f..1f,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun CustomColorDot(
  selected: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(
          Brush.sweepGradient(
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
          ),
        ).border(
          width = if (selected) 2.dp else 1.dp,
          color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
          shape = CircleShape,
        ).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (selected) {
      Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/** Pick any colour: hue, strength and brightness. */
@Composable
private fun ColorPickerDialog(
  initial: Color,
  onDismiss: () -> Unit,
  onPick: (Color) -> Unit,
) {
  val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
  var hue by remember { mutableStateOf(start[0]) }
  var strength by remember { mutableStateOf(start[1].coerceIn(0.15f, 1f)) }
  var brightness by remember { mutableStateOf(start[2].coerceIn(0.3f, 1f)) }
  val picked = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, strength, brightness)))

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Choose your color") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(picked),
        )
        Text(text = "Color", style = MaterialTheme.typography.bodyMedium)
        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
        Text(text = "Strength", style = MaterialTheme.typography.bodyMedium)
        Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.15f..1f)
        Text(text = "Brightness", style = MaterialTheme.typography.bodyMedium)
        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0.3f..1f)
      }
    },
    confirmButton = { TextButton(onClick = { onPick(picked) }) { Text("Select") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
