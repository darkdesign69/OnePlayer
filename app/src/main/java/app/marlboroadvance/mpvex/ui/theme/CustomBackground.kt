package app.marlboroadvance.mpvex.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import org.koin.compose.koinInject
import java.io.File

/** Customize Theme: background modes are stored as "none", "image" or "preset:<id>". */
const val CUSTOM_BG_NONE = "none"
const val CUSTOM_BG_IMAGE = "image"
private const val PRESET_PREFIX = "preset:"

class BackgroundPreset(
  val id: String,
  val colors: List<Color>,
) {
  fun brush(): Brush =
    if (colors.size == 1) SolidColor(colors[0]) else Brush.verticalGradient(colors)
}

val BackgroundPresets =
  listOf(
    BackgroundPreset("black", listOf(Color(0xFF000000))),
    BackgroundPreset("charcoal", listOf(Color(0xFF37474F), Color(0xFF121212))),
    BackgroundPreset("purple", listOf(Color(0xFF7B1FA2), Color(0xFF311B92))),
    BackgroundPreset("blue", listOf(Color(0xFF1976D2), Color(0xFF0D1B4D))),
    BackgroundPreset("green", listOf(Color(0xFF2E7D32), Color(0xFF0B2E13))),
    BackgroundPreset("sunset", listOf(Color(0xFFFF7043), Color(0xFF6A1B9A))),
    BackgroundPreset("ocean", listOf(Color(0xFF00ACC1), Color(0xFF0D47A1))),
    BackgroundPreset("rose", listOf(Color(0xFFEC407A), Color(0xFF4A148C))),
  )

val AccentColors =
  listOf(
    Color(0xFF43A047),
    Color(0xFFFFB300),
    Color(0xFFFF5722),
    Color(0xFFEC407A),
    Color(0xFFC0CA33),
    Color(0xFF00BCD4),
    Color(0xFF26A69A),
    Color(0xFF2196F3),
    Color(0xFF7E57C2),
    Color(0xFFE53935),
  )

fun presetMode(id: String): String = PRESET_PREFIX + id

fun backgroundPresetFor(mode: String): BackgroundPreset? =
  if (mode.startsWith(PRESET_PREFIX)) {
    val id = mode.removePrefix(PRESET_PREFIX)
    BackgroundPresets.firstOrNull { it.id == id }
  } else {
    null
  }

fun backgroundImageFile(context: Context): File = File(context.filesDir, "custom_background.jpg")

/** A custom background is drawn only for a valid preset or when a saved picture exists. */
fun isCustomBackgroundActive(
  context: Context,
  mode: String,
): Boolean =
  when {
    mode == CUSTOM_BG_IMAGE -> backgroundImageFile(context).exists()
    else -> backgroundPresetFor(mode) != null
  }

/**
 * Copies the picked picture into the app's private storage, scaled down so it is safe to draw.
 * Returns true when the picture was saved.
 */
suspend fun saveBackgroundImage(
  context: Context,
  uri: Uri,
): Boolean =
  withContext(Dispatchers.IO) {
    try {
      val resolver = context.contentResolver
      val bitmap: Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longestSide = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longestSide / sample > 2400) sample *= 2
            if (sample > 1) decoder.setTargetSampleSize(sample)
          }
        } else {
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
          val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
          var sample = 1
          while (longestSide / sample > 2400) sample *= 2
          val options = BitmapFactory.Options().apply { inSampleSize = sample }
          resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }

      if (bitmap == null) {
        false
      } else {
        try {
          backgroundImageFile(context).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
          }
        } finally {
          bitmap.recycle()
        }
        true
      }
    } catch (e: Exception) {
      false
    }
  }

@Composable
fun rememberBackgroundBitmap(
  mode: String,
  version: Int,
): ImageBitmap? {
  val context = LocalContext.current
  val bitmap by produceState<ImageBitmap?>(initialValue = null, mode, version) {
    value =
      if (mode == CUSTOM_BG_IMAGE) {
        withContext(Dispatchers.IO) {
          try {
            BitmapFactory.decodeFile(backgroundImageFile(context).absolutePath)?.asImageBitmap()
          } catch (e: Throwable) {
            null
          }
        }
      } else {
        null
      }
  }
  return bitmap
}

/** Draws the chosen background (picture or colour/gradient). [blur] and [dim] (the dark overlay) are 0..1. */
@Composable
fun CustomBackgroundLayer(
  mode: String,
  version: Int,
  blur: Float,
  modifier: Modifier = Modifier,
  dim: Float? = null,
) {
  // the dark overlay comes from the saved setting unless a value is given (the live preview gives one)
  val dimPrefs = koinInject<AppearancePreferences>()
  val savedDim by dimPrefs.customBackgroundDim.collectAsState()
  val dimAmount = dim ?: savedDim
  val preset = backgroundPresetFor(mode)
  val image = rememberBackgroundBitmap(mode, version)

  Box(modifier = modifier) {
    if (preset != null) {
      Box(modifier = Modifier.fillMaxSize().background(preset.brush()))
    } else if (image != null) {
      val blurModifier = if (blur > 0.01f) Modifier.blur((blur * 24f).dp) else Modifier
      Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer {
              scaleX = 1.1f
              scaleY = 1.1f
            }.then(blurModifier),
      )
    }
    // Dark overlay: 0 = the picture as it is, 1 = almost black
    if (dimAmount > 0.01f) {
      Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = (dimAmount * 0.9f).coerceIn(0f, 0.9f))))
    }
  }
}

/** Makes the app's screens and panels see-through so the background shows. */
fun ColorScheme.withTransparentPanels(transparency: Float): ColorScheme {
  val t = transparency.coerceIn(0f, 1f)
  val panelAlpha = 1f - t * 0.85f
  // Selected items, chips and buttons stay a little more solid so they remain easy to see
  val accentAlpha = 1f - t * 0.55f
  return copy(
    background = Color.Transparent,
    surface = Color.Transparent,
    surfaceDim = surfaceDim.copy(alpha = panelAlpha),
    surfaceContainerLowest = surfaceContainerLowest.copy(alpha = panelAlpha),
    surfaceContainerLow = surfaceContainerLow.copy(alpha = panelAlpha),
    surfaceContainer = surfaceContainer.copy(alpha = panelAlpha),
    surfaceVariant = surfaceVariant.copy(alpha = panelAlpha),
    primaryContainer = primaryContainer.copy(alpha = accentAlpha),
    secondaryContainer = secondaryContainer.copy(alpha = accentAlpha),
    tertiaryContainer = tertiaryContainer.copy(alpha = accentAlpha),
  )
}

/** Applies the user's own colour across the whole app: highlights, buttons, bars and tinted panels. */
fun ColorScheme.withAccent(accent: Color): ColorScheme {
  fun onColor(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White
  val secondaryTone = lerp(accent, onSurface, 0.30f)
  val tertiaryTone = lerp(accent, onSurface, 0.45f)
  val container = lerp(surfaceContainerHigh, accent, 0.35f)
  val softContainer = lerp(surfaceContainerHigh, accent, 0.25f)
  return copy(
    primary = accent,
    onPrimary = onColor(accent),
    primaryContainer = container,
    onPrimaryContainer = onSurface,
    inversePrimary = lerp(accent, Color.White, 0.4f),
    secondary = secondaryTone,
    onSecondary = onColor(secondaryTone),
    secondaryContainer = softContainer,
    onSecondaryContainer = onSurface,
    tertiary = tertiaryTone,
    onTertiary = onColor(tertiaryTone),
    tertiaryContainer = softContainer,
    onTertiaryContainer = onSurface,
    surfaceTint = accent,
    surfaceContainerLowest = lerp(surfaceContainerLowest, accent, 0.06f),
    surfaceContainerLow = lerp(surfaceContainerLow, accent, 0.08f),
    surfaceContainer = lerp(surfaceContainer, accent, 0.10f),
    surfaceContainerHigh = lerp(surfaceContainerHigh, accent, 0.12f),
    surfaceContainerHighest = lerp(surfaceContainerHighest, accent, 0.14f),
    surfaceVariant = lerp(surfaceVariant, accent, 0.10f),
    outlineVariant = lerp(outlineVariant, accent, 0.20f),
  )
}

/** Colour of the soft layer drawn over the background picture; it takes on the user's colour. */
fun backgroundScrim(
  base: Color,
  accent: Int,
): Color = (if (accent != 0) lerp(base, Color(accent), 0.35f) else base).copy(alpha = 0.28f)

/** The user's chosen colour, or [default] when none was chosen. */
@Composable
fun rememberAccentOrDefault(default: Color): Color {
  val preferences = koinInject<AppearancePreferences>()
  val accent by preferences.customAccent.collectAsState()
  return if (accent != 0) Color(accent) else default
}

/** Colours the two-tone folder picture with the user's colour; null keeps the original blue. */
@Composable
fun rememberAccentColorFilter(): ColorFilter? {
  val preferences = koinInject<AppearancePreferences>()
  val accent by preferences.customAccent.collectAsState()
  return remember(accent) {
    if (accent == 0) {
      null
    } else {
      val c = Color(accent)
      val s = 1.75f
      val wr = 0.299f * s
      val wg = 0.587f * s
      val wb = 0.114f * s
      ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            wr * c.red, wg * c.red, wb * c.red, 0f, 0f,
            wr * c.green, wg * c.green, wb * c.green, 0f, 0f,
            wr * c.blue, wg * c.blue, wb * c.blue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          ),
        ),
      )
    }
  }
}
