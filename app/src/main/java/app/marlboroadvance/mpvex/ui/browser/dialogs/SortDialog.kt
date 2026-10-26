package app.marlboroadvance.mpvex.ui.browser.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * "Sort & View Options" window. Laid out compactly, like MX Player: view mode and layout side by side,
 * a grid of sort choices, the order, grid columns, then collapsible Fields and Advanced sections.
 */
@Composable
fun SortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  title: String,
  sortType: String,
  onSortTypeChange: (String) -> Unit,
  sortOrderAsc: Boolean,
  onSortOrderChange: (Boolean) -> Unit,
  types: List<String>,
  icons: List<ImageVector>,
  getLabelForType: (String, Boolean) -> Pair<String, String>,
  modifier: Modifier = Modifier,
  visibilityToggles: List<VisibilityToggle> = emptyList(),
  viewModeSelector: ViewModeSelector? = null,
  layoutModeSelector: ViewModeSelector? = null,
  folderGridColumnSelector: GridColumnSelector? = null,
  videoGridColumnSelector: GridColumnSelector? = null,
  showSortOptions: Boolean = true,
  enableViewModeOptions: Boolean = true,
  enableLayoutModeOptions: Boolean = true,
  advancedToggles: List<VisibilityToggle> = emptyList(),
) {
  if (!isOpen) return

  val (ascLabel, descLabel) = getLabelForType(sortType, sortOrderAsc)

  // Slightly smaller than normal, and the list scrolls inside when it would fill the screen
  val scale = 0.92f
  val outerDensity = LocalDensity.current
  val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.66f / scale).dp

  CompositionLocalProvider(LocalDensity provides Density(outerDensity.density * scale, outerDensity.fontScale)) {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = null,
      text = {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .heightIn(max = maxContentHeight)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (viewModeSelector != null || layoutModeSelector != null) {
            val leftCount = if (viewModeSelector?.thirdOptionLabel != null) 3 else 2
            Row(
              modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
              verticalAlignment = Alignment.Top,
            ) {
              if (viewModeSelector != null) {
                ModeSelector(
                  selector = viewModeSelector,
                  enabled = enableViewModeOptions,
                  modifier = Modifier.weight(if (layoutModeSelector != null) leftCount.toFloat() else 1f),
                )
              }
              if (viewModeSelector != null && layoutModeSelector != null) {
                VerticalDivider(
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                  color = MaterialTheme.colorScheme.outlineVariant,
                )
              }
              if (layoutModeSelector != null) {
                ModeSelector(
                  selector = layoutModeSelector,
                  enabled = enableLayoutModeOptions,
                  modifier = Modifier.weight(if (viewModeSelector != null) 2f else 1f),
                )
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }

          GridColumnsSection(
            folderGridColumnSelector = folderGridColumnSelector,
            videoGridColumnSelector = videoGridColumnSelector,
          )

          if (showSortOptions) {
            SectionTitle("Sort")
            GridOf(
              columns = 5,
              count = types.size,
            ) { index, cellModifier ->
              OptionCell(
                icon = icons.getOrNull(index),
                label = types[index],
                selected = sortType == types[index],
                enabled = true,
                modifier = cellModifier,
                onClick = { onSortTypeChange(types[index]) },
              )
            }
            Spacer(Modifier.height(4.dp))
            SortOrderSelector(
              sortOrderAsc = sortOrderAsc,
              onSortOrderChange = onSortOrderChange,
              ascLabel = ascLabel,
              descLabel = descLabel,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }

          if (advancedToggles.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CollapsibleSection(title = "Advanced", startExpanded = true) {
              Column(modifier = Modifier.fillMaxWidth()) {
                advancedToggles.forEach { toggle ->
                  Row(
                    modifier =
                      Modifier
                        .fillMaxWidth()
                        .clickable { toggle.onCheckedChange(!toggle.checked) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = toggle.label,
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.weight(1f),
                    )
                    Switch(checked = toggle.checked, onCheckedChange = null)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onDismiss) {
          Text("Done", fontWeight = FontWeight.Bold)
        }
      },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
      shape = RoundedCornerShape(16.dp),
      modifier = modifier.widthIn(min = 280.dp),
    )
  }
}

data class VisibilityToggle(
  val label: String,
  val checked: Boolean,
  val onCheckedChange: (Boolean) -> Unit,
)

data class ViewModeSelector(
  val label: String,
  val firstOptionLabel: String,
  val secondOptionLabel: String,
  val firstOptionIcon: ImageVector,
  val secondOptionIcon: ImageVector,
  val isFirstOptionSelected: Boolean,
  val onViewModeChange: (Boolean) -> Unit,
  /** Optional third choice (used for the "Files" view that lists every video). */
  val thirdOptionLabel: String? = null,
  val thirdOptionIcon: ImageVector? = null,
  val isThirdOptionSelected: Boolean = false,
  val onThirdOptionClick: (() -> Unit)? = null,
)

data class GridColumnSelector(
  val label: String,
  val currentValue: Int,
  val onValueChange: (Int) -> Unit,
  val valueRange: ClosedFloatingPointRange<Float> = 1f..4f,
  val steps: Int = 2,
)

// -----------------------------------------------------------------------------
// Pieces of the window
// -----------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
  )
}

/** Lays [count] items out in rows of [columns] equal cells. */
@Composable
private fun GridOf(
  columns: Int,
  count: Int,
  cell: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    var start = 0
    while (start < count) {
      Row(modifier = Modifier.fillMaxWidth()) {
        for (i in 0 until columns) {
          val index = start + i
          if (index < count) {
            cell(index, Modifier.weight(1f))
          } else {
            Spacer(Modifier.weight(1f))
          }
        }
      }
      start += columns
    }
  }
}

/** A small icon with its name underneath; the chosen one is drawn in the accent colour. */
@Composable
private fun OptionCell(
  icon: ImageVector?,
  label: String,
  selected: Boolean,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val color =
    when {
      !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
      selected -> MaterialTheme.colorScheme.primary
      else -> MaterialTheme.colorScheme.onSurface
    }
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 8.dp, horizontal = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (icon != null) {
      Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = color,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun ModeSelector(
  selector: ViewModeSelector,
  enabled: Boolean,
  modifier: Modifier = Modifier,
) {
  val hasThird = selector.thirdOptionLabel != null && selector.thirdOptionIcon != null
  val selectedIndex =
    when {
      hasThird && selector.isThirdOptionSelected -> 2
      selector.isFirstOptionSelected -> 0
      else -> 1
    }
  val labels =
    if (hasThird) {
      listOf(selector.firstOptionLabel, selector.secondOptionLabel, selector.thirdOptionLabel!!)
    } else {
      listOf(selector.firstOptionLabel, selector.secondOptionLabel)
    }
  val iconList =
    if (hasThird) {
      listOf(selector.firstOptionIcon, selector.secondOptionIcon, selector.thirdOptionIcon!!)
    } else {
      listOf(selector.firstOptionIcon, selector.secondOptionIcon)
    }
  Column(modifier = modifier) {
    SectionTitle(selector.label)
    GridOf(columns = labels.size, count = labels.size) { index, cellModifier ->
      OptionCell(
        icon = iconList[index],
        label = labels[index],
        selected = index == selectedIndex,
        enabled = enabled,
        modifier = cellModifier,
        onClick = {
          when (index) {
            0 -> selector.onViewModeChange(true)
            1 -> selector.onViewModeChange(false)
            else -> selector.onThirdOptionClick?.invoke()
          }
        },
      )
    }
  }
}

@Composable
private fun SortOrderSelector(
  sortOrderAsc: Boolean,
  onSortOrderChange: (Boolean) -> Unit,
  ascLabel: String,
  descLabel: String,
  modifier: Modifier = Modifier,
) {
  val options = listOf(ascLabel, descLabel)
  val selectedIndex = if (sortOrderAsc) 0 else 1

  SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
    options.forEachIndexed { index, label ->
      SegmentedButton(
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
        onClick = { onSortOrderChange(index == 0) },
        selected = index == selectedIndex,
        icon = {
          Icon(
            if (index == 0) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        },
      ) {
        Text(label, maxLines = 1)
      }
    }
  }
}

/** A heading with an arrow that opens and closes what is under it. */
@Composable
private fun CollapsibleSection(
  title: String,
  startExpanded: Boolean,
  content: @Composable () -> Unit,
) {
  var expanded by remember { mutableStateOf(startExpanded) }
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
        .padding(horizontal = 8.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    Icon(
      imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
      contentDescription = if (expanded) "Collapse" else "Expand",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  if (expanded) content()
}

@Composable
private fun ColumnSlider(
  title: String,
  selector: GridColumnSelector,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
      value = selector.currentValue.toFloat(),
      onValueChange = { selector.onValueChange(it.toInt()) },
      valueRange = selector.valueRange,
      steps = selector.steps,
      modifier = Modifier.fillMaxWidth(),
    )
    Text(
      text = "${selector.currentValue} columns",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
  }
}

@Composable
private fun GridColumnsSection(
  folderGridColumnSelector: GridColumnSelector?,
  videoGridColumnSelector: GridColumnSelector?,
) {
  if (folderGridColumnSelector == null && videoGridColumnSelector == null) return

  SectionTitle("Grid columns")
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (folderGridColumnSelector != null) {
      ColumnSlider("Folder grid", folderGridColumnSelector, Modifier.weight(1f))
    }
    if (videoGridColumnSelector != null) {
      ColumnSlider("Video grid", videoGridColumnSelector, Modifier.weight(1f))
    }
  }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 4.dp))
}
