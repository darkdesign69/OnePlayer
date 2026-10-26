package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import app.marlboroadvance.mpvex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistScreen
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.NetworkStreamingScreen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_preferences),
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
      ProvidePreferenceLocals {
        LazyColumn(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          // Search bar - full width, prominent placement
          item {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { backstack.add(SettingsSearchScreen) },
              shape = RoundedCornerShape(28.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              tonalElevation = 2.dp,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = stringResource(R.string.settings_search_hint),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }
          
          // Library section (these used to be tabs at the bottom of the app)
          item {
            PreferenceSectionHeader(title = "Library")
          }

          item {
            PreferenceCard {
              Preference(
                title = { Text(text = "Recents") },
                summary = {
                  Text(
                    text = "Videos you played recently",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(RecentlyPlayedScreen) },
              )

              PreferenceDivider()

              Preference(
                title = { Text(text = "Playlists") },
                summary = {
                  Text(
                    text = "Your video playlists",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.AutoMirrored.Outlined.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(PlaylistScreen) },
              )

              PreferenceDivider()

              Preference(
                title = { Text(text = "Network") },
                summary = {
                  Text(
                    text = "Stream from network shares and links",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(NetworkStreamingScreen) },
              )
            }
          }

          // UI & Appearance Section
          item {
            PreferenceSectionHeader(title = "UI & Appearance")
          }
          
          item {
            PreferenceCard {
              Preference(
                title = { Text(text = stringResource(id = R.string.pref_appearance_title)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_appearance_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Palette, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(AppearancePreferencesScreen) },
              )
              
              PreferenceDivider()
              
              Preference(
                title = { Text(text = "Customize theme") },
                summary = {
                  Text(
                    text = "Background picture or color, accent color, transparency and blur",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(CustomizeThemeScreen) },
              )

              PreferenceDivider()

              Preference(

                title = { Text(text = stringResource(id = R.string.pref_layout_title)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_layout_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.AutoMirrored.Outlined.ViewQuilt, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(PlayerControlsPreferencesScreen) },
              )

              PreferenceDivider()

              Preference(
                title = { Text(text = "Font") },
                summary = {
                  Text(
                    text = "Font family, custom font, size, bold and italic",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(FontSettingsScreen) },
              )
            }
          }
          
          // Playback & Controls Section
          item {
            PreferenceSectionHeader(title = "Playback & Controls")
          }
          
          item {
            PreferenceCard {
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_player)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_player_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.PlayCircle, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(PlayerPreferencesScreen) },
              )
              
              PreferenceDivider()
              
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_gesture)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_gesture_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Gesture, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(GesturePreferencesScreen) },
              )
            }
          }
          
          // File Management Section
          item {
            PreferenceSectionHeader(title = "File Management")
          }
          
          item {
            PreferenceCard {
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_folders_title)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_folders_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Folder, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(FoldersPreferencesScreen) },
              )
            }
          }
          
          // Media Settings Section
          item {
            PreferenceSectionHeader(title = "Media Settings")
          }
          
          item {
            PreferenceCard {
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_decoder)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_decoder_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Memory, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(DecoderPreferencesScreen) },
              )
              
              PreferenceDivider()
              
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_subtitles)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_subtitles_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Subtitles, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(SubtitlesPreferencesScreen) },
              )
              
              PreferenceDivider()
              
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_audio)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_audio_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Audiotrack, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(AudioPreferencesScreen) },
              )
            }
          }
          
          // Backup & Restore Section
          item {
            PreferenceSectionHeader(title = "Backup & Restore")
          }

          item {
            PreferenceCard {
              BackupRestoreRows()
            }
          }

          item {
            Text(
              text = "OnlyPlay  •  Created by darkdesign69",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            )
          }
        }
      }
    }
  }
}
