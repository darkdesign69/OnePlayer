package app.marlboroadvance.mpvex.ui.browser.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private data class MusicTrack(
  val id: Long,
  val title: String,
  val artist: String,
  val durationMs: Long,
  val uri: Uri,
)

/** Lists the music (audio files) on the device. Tap a track to play it. */
@Serializable
object MusicScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current

    val permission =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
      } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
      }
    var granted by remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
      )
    }
    val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
      }

    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(granted, reloadKey) {
      if (granted) {
        loading = true
        tracks = withContext(Dispatchers.IO) { loadTracks(context) }
      }
      loading = false
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = "Music", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
          },
          actions = {
            IconButton(
              enabled = granted && !scanning,
              onClick = {
                scanning = true
                val root = android.os.Environment.getExternalStorageDirectory().absolutePath
                MediaScannerConnection.scanFile(context, arrayOf(root), null) { _, _ ->
                  reloadKey += 1
                  scanning = false
                }
              },
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = "Scan phone for music")
            }
          },
        )
      },
    ) { padding ->
      Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
      ) {
        when {
          !granted -> {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.padding(24.dp),
            ) {
              Text(
                text = "Allow access to your music files to list them here.",
                style = MaterialTheme.typography.bodyLarge,
              )
              Button(onClick = { permissionLauncher.launch(permission) }) {
                Text("Allow access")
              }
            }
          }
          loading || scanning -> CircularProgressIndicator()
          tracks.isEmpty() -> {
            Text(
              text = "No music found on this device",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.outline,
            )
          }
          else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(tracks, key = { it.id }) { track ->
                TrackRow(track = track, onClick = { playTrack(context, track) })
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TrackRow(
  track: MusicTrack,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      modifier =
        Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(12.dp))
          .then(Modifier),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${track.artist}  •  ${formatTrackTime(track.durationMs)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun playTrack(
  context: Context,
  track: MusicTrack,
) {
  val intent = Intent(Intent.ACTION_VIEW, track.uri)
  intent.setClass(context, PlayerActivity::class.java)
  intent.putExtra("internal_launch", true)
  intent.putExtra("title", track.title)
  context.startActivity(intent)
}

private fun formatTrackTime(durationMs: Long): String {
  val totalSeconds = (durationMs / 1000).toInt().coerceAtLeast(0)
  val hours = totalSeconds / 3600
  val minutes = totalSeconds % 3600 / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) {
    "%d:%02d:%02d".format(hours, minutes, seconds)
  } else {
    "%d:%02d".format(minutes, seconds)
  }
}

private fun loadTracks(context: Context): List<MusicTrack> {
  val collection =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
  val projection =
    arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.DURATION,
    )
  val result = mutableListOf<MusicTrack>()
  try {
    context.contentResolver
      .query(
        collection,
        projection,
        "${MediaStore.Audio.Media.DURATION} >= 10000 AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0",
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
      )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cursor.moveToNext()) {
          val id = cursor.getLong(idColumn)
          val artist = cursor.getString(artistColumn)
          result.add(
            MusicTrack(
              id = id,
              title = cursor.getString(titleColumn) ?: "Unknown title",
              artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Unknown artist" else artist,
              durationMs = cursor.getLong(durationColumn),
              uri = android.content.ContentUris.withAppendedId(collection, id),
            ),
          )
        }
      }
  } catch (e: Exception) {
    // Return whatever was read; an empty list shows the "No music found" message.
  }
  return result
}
