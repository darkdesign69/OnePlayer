package app.marlboroadvance.mpvex.ui.browser.folderlist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shortcut chips shown above the folder list: Music and Privacy. */
@Composable
fun LibraryChips(
  onMusicClick: () -> Unit,
  onPrivacyClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    LibraryChip(icon = Icons.Filled.MusicNote, label = "Music", highlighted = true, onClick = onMusicClick)
    LibraryChip(icon = Icons.Filled.Lock, label = "Privacy", onClick = onPrivacyClick)
  }
}

@Composable
private fun LibraryChip(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  highlighted: Boolean = false,
) {
  val container = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
  val content = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(50),
    color = container,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = content,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = content,
      )
    }
  }
}
