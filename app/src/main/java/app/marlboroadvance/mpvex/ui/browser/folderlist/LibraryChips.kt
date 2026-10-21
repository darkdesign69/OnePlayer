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
        .padding(horizontal = 12.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    LibraryChip(icon = Icons.Filled.MusicNote, label = "Music", onClick = onMusicClick)
    LibraryChip(icon = Icons.Filled.Lock, label = "Privacy", onClick = onPrivacyClick)
  }
}

@Composable
private fun LibraryChip(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}
