package app.marlboroadvance.mpvex.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/** Material 3 shape scale with slightly softer corners than the default. */
val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

private fun Color.towardsGrey(amount: Float): Color {
  val grey = luminance().pow(1f / 2.2f)
  return lerp(this, Color(grey, grey, grey, alpha), amount)
}

/**
 * Calmer, more neutral surfaces for a minimal look: backgrounds and containers lose most of their
 * colour tint, while the accent colours (primary, secondary, tertiary) are left alone.
 */
fun ColorScheme.withNeutralSurfaces(amount: Float = 0.55f): ColorScheme =
  copy(
    background = background.towardsGrey(amount),
    surface = surface.towardsGrey(amount),
    surfaceDim = surfaceDim.towardsGrey(amount),
    surfaceBright = surfaceBright.towardsGrey(amount),
    surfaceContainerLowest = surfaceContainerLowest.towardsGrey(amount),
    surfaceContainerLow = surfaceContainerLow.towardsGrey(amount),
    surfaceContainer = surfaceContainer.towardsGrey(amount),
    surfaceContainerHigh = surfaceContainerHigh.towardsGrey(amount),
    surfaceContainerHighest = surfaceContainerHighest.towardsGrey(amount),
    surfaceVariant = surfaceVariant.towardsGrey(amount),
    outlineVariant = outlineVariant.towardsGrey(amount),
  )
