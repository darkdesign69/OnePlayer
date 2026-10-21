package app.marlboroadvance.mpvex.ui.browser.music

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import app.marlboroadvance.mpvex.R
import java.io.File

private val LocalFontApplied = compositionLocalOf { false }

/** Font weights that follow the "Bold" font option (use these instead of FontWeight.Normal / Medium). */
internal object MusicFont {
  val normal: FontWeight get() = if (MusicSettings.fontBold) FontWeight.Bold else FontWeight.Normal
  val medium: FontWeight get() = if (MusicSettings.fontBold) FontWeight.Bold else FontWeight.Medium
  val semi: FontWeight get() = if (MusicSettings.fontBold) FontWeight.Bold else FontWeight.SemiBold
}

internal val builtInFontNames = listOf("Default (system)", "Anime Ace", "Source Code Pro", "Super Waffles", "Stay With Me")

/** 0 = system font, 1-4 = fonts that come with the app, 5 = the font file the user added. */
internal fun buildFontFamily(
  choice: Int,
  customPath: String,
  bold: Boolean,
  italic: Boolean,
): FontFamily? =
  when (choice) {
    1 ->
      FontFamily(
        Font(R.font.animeace2_reg, FontWeight.Normal),
        Font(R.font.animeace2_bld, FontWeight.Bold),
        Font(R.font.animeace2_ital, FontWeight.Normal, FontStyle.Italic),
      )
    2 -> FontFamily(Font(R.font.sourcecodepro_regular))
    3 -> FontFamily(Font(R.font.super_waffles))
    4 -> FontFamily(Font(R.font.stay_with_me))
    5 ->
      runCatching {
        val base = Typeface.createFromFile(customPath)
        val style =
          when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
          }
        FontFamily(Typeface.create(base, style))
      }.getOrNull()
    else -> null
  }

private fun TextUnit.scaled(f: Float): TextUnit = if (this.isSpecified) this * f else this

private fun TextStyle.restyle(
  family: FontFamily?,
  scale: Float,
  bold: Boolean,
  italic: Boolean,
): TextStyle {
  var s = this
  if (family != null) s = s.copy(fontFamily = family)
  if (scale != 1f) s = s.copy(fontSize = s.fontSize.scaled(scale), lineHeight = s.lineHeight.scaled(scale))
  if (bold) s = s.copy(fontWeight = FontWeight.Bold)
  if (italic) s = s.copy(fontStyle = FontStyle.Italic)
  return s
}

/** Applies the font, size, bold and italic options to every text of the player (dialogs and menus too). */
@Composable
internal fun MusicTypographyTheme(content: @Composable () -> Unit) {
  // Already applied by an outer theme (the whole app): do not apply it twice.
  if (LocalFontApplied.current) {
    content()
    return
  }
  MusicSettings.init(LocalContext.current)
  val choice = MusicSettings.fontChoice
  val path = MusicSettings.customFontPath
  val bold = MusicSettings.fontBold
  val italic = MusicSettings.fontItalic
  val scale = MusicSettings.fontScale / 100f
  val family = remember(choice, path, bold, italic) { buildFontFamily(choice, path, bold, italic) }
  if (family == null && scale == 1f && !bold && !italic) {
    CompositionLocalProvider(LocalFontApplied provides true, content = content)
  } else {
    val base = MaterialTheme.typography
    val typo =
      remember(base, family, scale, bold, italic) {
        base.copy(
          displayLarge = base.displayLarge.restyle(family, scale, bold, italic),
          displayMedium = base.displayMedium.restyle(family, scale, bold, italic),
          displaySmall = base.displaySmall.restyle(family, scale, bold, italic),
          headlineLarge = base.headlineLarge.restyle(family, scale, bold, italic),
          headlineMedium = base.headlineMedium.restyle(family, scale, bold, italic),
          headlineSmall = base.headlineSmall.restyle(family, scale, bold, italic),
          titleLarge = base.titleLarge.restyle(family, scale, bold, italic),
          titleMedium = base.titleMedium.restyle(family, scale, bold, italic),
          titleSmall = base.titleSmall.restyle(family, scale, bold, italic),
          bodyLarge = base.bodyLarge.restyle(family, scale, bold, italic),
          bodyMedium = base.bodyMedium.restyle(family, scale, bold, italic),
          bodySmall = base.bodySmall.restyle(family, scale, bold, italic),
          labelLarge = base.labelLarge.restyle(family, scale, bold, italic),
          labelMedium = base.labelMedium.restyle(family, scale, bold, italic),
          labelSmall = base.labelSmall.restyle(family, scale, bold, italic),
        )
      }
    CompositionLocalProvider(LocalFontApplied provides true) {
      MaterialTheme(typography = typo, content = content)
    }
  }
}

private fun displayName(
  context: Context,
  uri: Uri,
): String? =
  runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
  }.getOrNull()

/**
 * Copies a font file the user picked (.ttf or .otf) into the app and starts using it.
 * Returns the file name, or null when the file is not a valid font.
 */
internal fun importCustomFont(
  context: Context,
  uri: Uri,
): String? {
  val name = displayName(context, uri) ?: uri.lastPathSegment ?: "font.ttf"
  val ext = name.substringAfterLast('.', "").lowercase()
  if (ext != "ttf" && ext != "otf") return null
  val dir = File(context.filesDir, "fonts").apply { mkdirs() }
  val out = File(dir, "custom_${System.currentTimeMillis()}.$ext")
  try {
    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
    val head = ByteArray(4)
    out.inputStream().use { it.read(head) }
    val tag = String(head, Charsets.ISO_8859_1)
    val trueType = head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte()
    if (!(trueType || tag == "OTTO" || tag == "true")) {
      out.delete()
      return null
    }
    Typeface.createFromFile(out)
  } catch (_: Exception) {
    out.delete()
    return null
  }
  dir.listFiles()?.filter { it.name.startsWith("custom_") && it != out }?.forEach { it.delete() }
  MusicSettings.customFontPath = out.absolutePath
  MusicSettings.customFontName = name
  MusicSettings.fontChoice = 5
  MusicSettings.save()
  return name
}

internal fun removeCustomFont(context: Context) {
  File(context.filesDir, "fonts").listFiles()?.forEach { it.delete() }
  MusicSettings.customFontPath = ""
  MusicSettings.customFontName = ""
  if (MusicSettings.fontChoice == 5) MusicSettings.fontChoice = 0
  MusicSettings.save()
}
