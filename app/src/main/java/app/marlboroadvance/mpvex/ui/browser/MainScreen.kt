package app.marlboroadvance.mpvex.ui.browser

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.ui.browser.music.HomeMiniAudioBar
import app.marlboroadvance.mpvex.ui.browser.music.MusicNav
import app.marlboroadvance.mpvex.ui.browser.music.MusicScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.folderlist.FolderListScreen
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.NetworkStreamingScreen
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistScreen
import app.marlboroadvance.mpvex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
object MainScreen : Screen {
  // Use a companion object to store state more persistently
  private var persistentSelectedTab: Int = 0

  // Reactive shared state that can be updated by FileSystemBrowserScreen / selection handlers
  private val _isInSelectionMode = MutableStateFlow(false)
  val isInSelectionMode: StateFlow<Boolean> = _isInSelectionMode.asStateFlow()

  private val _shouldHideNavigationBar = MutableStateFlow(false)
  val shouldHideNavigationBar: StateFlow<Boolean> = _shouldHideNavigationBar.asStateFlow()

  private val _sharedVideoSelectionManager = MutableStateFlow<Any?>(null)
  val sharedVideoSelectionManager: StateFlow<Any?> = _sharedVideoSelectionManager.asStateFlow()

  private val _isPermissionDenied = MutableStateFlow(false)

  /**
   * Update selection state and navigation bar visibility
   * This method should be called whenever selection changes
   */
  fun updateSelectionState(
    isInSelectionMode: Boolean,
    isOnlyVideosSelected: Boolean,
    selectionManager: Any?
  ) {
    _isInSelectionMode.value = isInSelectionMode
    _sharedVideoSelectionManager.value = selectionManager
    // Only hide navigation bar when videos are selected AND in selection mode
    _shouldHideNavigationBar.value = isInSelectionMode && isOnlyVideosSelected
  }

  /**
   * Update permission state to control FAB visibility
   */
  fun updatePermissionState(isDenied: Boolean) {
    _isPermissionDenied.value = isDenied
  }

  /**
   * Get current permission denied state
   */
  fun getPermissionDeniedState(): Boolean = _isPermissionDenied.value

  /**
   * Update bottom navigation bar visibility based on floating bottom bar state
   */
  fun updateBottomBarVisibility(shouldShow: Boolean) {
    _shouldHideNavigationBar.value = !shouldShow
  }

  @Composable
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun Content() {
    // The bottom tab bar was removed. Recents, Playlists and Network are now in Settings.
    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
      val backstack = LocalBackStack.current
      Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          CompositionLocalProvider(
            LocalNavigationBarHeight provides 16.dp,
          ) {
            FolderListScreen.Content()
          }
        }
        // Mini player bar: shows the song that is playing, like XPlayer
        HomeMiniAudioBar(
          onOpenNow = {
            MusicNav.openNow = true
            backstack.add(MusicScreen)
          },
          onOpenLibrary = { backstack.add(MusicScreen) },
        )
      }
    }
  }
}

// CompositionLocal for navigation bar height
val LocalNavigationBarHeight = compositionLocalOf { 0.dp }