package app.marlboroadvance.mpvex.ui.browser.music

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lets the home screen ask the music screen to open straight on Now playing. */
internal object MusicNav {
  var openNow = false

  fun takeOpenNow(): Boolean {
    val v = openNow
    openNow = false
    return v
  }
}

private fun barTime(ms: Int): String {
  val total = (ms / 1000).coerceAtLeast(0)
  val h = total / 3600
  val m = total % 3600 / 60
  val s = total % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Small player bar for the home screen: shows the song that is playing (close, cover, title, time,
 * play/pause and a button for the music library). It only shows while a song is loaded.
 */
@Composable
fun HomeMiniAudioBar(
  onOpenNow: () -> Unit,
  onOpenLibrary: () -> Unit,
) {
  val t = MusicController.current ?: return
  val context = LocalContext.current
  var pos by remember { mutableIntStateOf(0) }
  LaunchedEffect(t.id, MusicController.isPlaying) {
    while (true) {
      pos = MusicController.position()
      delay(500)
    }
  }
  val art by produceState<Bitmap?>(null, t.id) { value = withContext(Dispatchers.IO) { loadArt(context, t, 160) } }
  MusicTypographyTheme {
    SolidThemed {
      Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).navigationBarsPadding()) {
        HorizontalDivider()
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          IconButton(onClick = { MusicController.stopAll() }) { Icon(Icons.Filled.Close, "Close player") }
          Row(
            modifier = Modifier.weight(1f).clickable(onClick = onOpenNow),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            val bmp = art
            Box(
              modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.secondaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
              } else {
                Icon(Icons.Filled.MusicNote, null)
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                barTime(pos) + "/" + barTime(t.durationMs.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
              )
            }
          }
          IconButton(onClick = { MusicController.togglePlay() }) {
            Icon(if (MusicController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(32.dp))
          }
          IconButton(onClick = onOpenLibrary) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Open music") }
        }
      }
    }
  }
}
