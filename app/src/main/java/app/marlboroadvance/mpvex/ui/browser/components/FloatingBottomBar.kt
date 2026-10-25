package app.marlboroadvance.mpvex.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Minimal Material 3 action bar for file/folder operations: plain icon buttons on a single
 * soft, pill-shaped surface.
 */
@Composable
fun BrowserBottomBar(
  isSelectionMode: Boolean,
  onCopyClick: () -> Unit,
  onMoveClick: () -> Unit,
  onRenameClick: () -> Unit,
  onDeleteClick: () -> Unit,
  onAddToPlaylistClick: () -> Unit,
  modifier: Modifier = Modifier,
  showCopy: Boolean = true,
  showMove: Boolean = true,
  showRename: Boolean = true,
  showDelete: Boolean = true,
  showAddToPlaylist: Boolean = true,
  onHideClick: (() -> Unit)? = null,
) {
  AnimatedVisibility(
    visible = isSelectionMode,
    modifier = modifier,
    enter = fadeIn(),
    exit = fadeOut(),
  ) {
    Surface(
      modifier = Modifier
        .windowInsetsPadding(WindowInsets.systemBars)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 0.dp,
      shadowElevation = 2.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        if (onHideClick != null) {
          BarButton(Icons.Filled.VisibilityOff, "Hide in Privacy", true, onHideClick)
        }
        BarButton(Icons.Filled.ContentCopy, "Copy", showCopy, onCopyClick)
        BarButton(Icons.AutoMirrored.Filled.DriveFileMove, "Move", showMove, onMoveClick)
        BarButton(Icons.Filled.DriveFileRenameOutline, "Rename", showRename, onRenameClick)
        BarButton(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Playlist", showAddToPlaylist, onAddToPlaylistClick)
        BarButton(Icons.Filled.Delete, "Delete", showDelete, onDeleteClick, danger = true)
      }
    }
  }
}

@Composable
private fun BarButton(
  icon: ImageVector,
  description: String,
  enabled: Boolean,
  onClick: () -> Unit,
  danger: Boolean = false,
) {
  IconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.size(48.dp),
    colors =
      if (danger) {
        IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
      } else {
        IconButtonDefaults.iconButtonColors()
      },
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      modifier = Modifier.size(24.dp),
    )
  }
}
