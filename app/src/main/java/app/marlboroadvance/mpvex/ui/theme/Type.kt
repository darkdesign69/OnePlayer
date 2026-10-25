package app.marlboroadvance.mpvex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val baseTypography = Typography()

/**
 * Minimal Material 3 type scale: the standard M3 sizes, with medium-weight titles, calm headlines
 * and slightly airier body text.
 */
val AppTypography =
  baseTypography.copy(
    headlineLarge = baseTypography.headlineLarge.copy(fontWeight = FontWeight.Normal),
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.Normal),
    headlineSmall = baseTypography.headlineSmall.copy(fontWeight = FontWeight.Normal),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = baseTypography.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = baseTypography.bodyLarge.copy(lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = baseTypography.bodyMedium.copy(lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelLarge = baseTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = baseTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
  )
