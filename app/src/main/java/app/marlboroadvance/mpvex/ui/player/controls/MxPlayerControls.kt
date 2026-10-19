package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.Decoder
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.VideoAspect
import app.marlboroadvance.mpvex.ui.player.controls.components.SeekbarWithTimers
import dev.vivvvek.seeker.Segment
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject

/** Buttons that already exist somewhere else in the MX layout (top bar / lock button). */
private val MX_EXCLUDED_BUTTONS =
  setOf(
    PlayerButton.BACK_ARROW,
    PlayerButton.VIDEO_TITLE,
    PlayerButton.LOCK_CONTROLS,
    PlayerButton.MORE_OPTIONS,
    PlayerButton.NONE,
  )

/**
 * MX Player style controls for portrait mode:
 *  - black top bar: back, title, decoder badge, more menu
 *  - round shortcut buttons over the top of the video
 *  - black bottom bar: seek bar with times, then lock + previous / play / next
 */
@Composable
fun MxPortraitControls(
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
  mediaTitle: String?,
  buttons: List<PlayerButton>,
  chapters: List<Segment>,
  currentChapter: Int?,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  decoder: Decoder,
  playbackSpeed: Float,
  paused: Boolean,
  position: Float,
  duration: Float,
  loopStart: Float?,
  loopEnd: Float?,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  onSeekingChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current

  val shortcutButtons = remember(buttons) { buttons.filter { it !in MX_EXCLUDED_BUTTONS } }
  var seeking by remember { mutableStateOf(false) }
  var showRemaining by remember { mutableStateOf(true) }
  var wasPausedBeforeSeek by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize()) {
    // ---------------- Top bar ----------------
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(Color.Black)
          .windowInsetsPadding(WindowInsets.statusBars),
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBackPress) {
          Icon(
            imageVector = Icons.AutoMirrored.Default.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
          )
        }
        Text(
          text = mediaTitle ?: "",
          color = Color.White,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier =
            Modifier
              .weight(1f)
              .padding(horizontal = 8.dp)
              .clickable(enabled = viewModel.hasPlaylistSupport()) {
                clickEvent()
                onOpenSheet(Sheets.Playlist)
              },
        )
        Text(
          text = decoder.title,
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          modifier =
            Modifier
              .clip(MaterialTheme.shapes.small)
              .clickable {
                clickEvent()
                onOpenSheet(Sheets.Decoders)
              }.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        IconButton(
          onClick = {
            clickEvent()
            onOpenSheet(Sheets.More)
          },
        ) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More",
            tint = Color.White,
          )
        }
      }
    }

    // ---------------- Round shortcut buttons over the video ----------------
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      shortcutButtons.forEach { button ->
        RenderPlayerButton(
          button = button,
          chapters = chapters,
          currentChapter = currentChapter,
          isPortrait = true,
          isSpeedNonOne = isSpeedNonOne,
          currentZoom = currentZoom,
          aspect = aspect,
          mediaTitle = mediaTitle,
          hideBackground = false,
          decoder = decoder,
          playbackSpeed = playbackSpeed,
          onBackPress = onBackPress,
          onOpenSheet = onOpenSheet,
          onOpenPanel = onOpenPanel,
          viewModel = viewModel,
          activity = activity,
          buttonSize = 44.dp,
        )
      }
    }

    // Empty middle: touches fall through to the gesture layer
    Spacer(modifier = Modifier.weight(1f))

    // ---------------- Bottom overlay: transparent, drawn over the video ----------------
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            ),
          ).windowInsetsPadding(WindowInsets.navigationBars)
          .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    ) {
      MxThinSeekBar(
        position = position,
        duration = duration,
        onSeekStart = {
          if (!seeking) {
            wasPausedBeforeSeek = paused
            if (!paused) viewModel.pause()
            seeking = true
            onSeekingChanged(true)
          }
          clickEvent()
        },
        onSeek = { value ->
          clickEvent()
          viewModel.seekTo(value.toInt())
        },
        onSeekEnd = {
          seeking = false
          onSeekingChanged(false)
          clickEvent()
          if (!wasPausedBeforeSeek) viewModel.unpause()
          viewModel.showControls()
        },
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = formatMxTime(position.toInt()),
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text =
            if (showRemaining) {
              "-" + formatMxTime((duration - position).toInt())
            } else {
              formatMxTime(duration.toInt())
            },
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          modifier =
            Modifier
              .clickable {
                clickEvent()
                showRemaining = !showRemaining
              },
        )
      }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(modifier = Modifier.weight(1f))

        Row(
          horizontalArrangement = Arrangement.spacedBy(28.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val hasPrevious = viewModel.hasPrevious()
          val hasNext = viewModel.hasNext()

          IconButton(
            onClick = {
              clickEvent()
              if (viewModel.hasPrevious()) viewModel.playPrevious()
            },
            enabled = hasPrevious,
          ) {
            Icon(
              imageVector = Icons.Default.SkipPrevious,
              contentDescription = "Previous",
              tint = Color.White.copy(alpha = if (hasPrevious) 1f else 0.38f),
              modifier = Modifier.size(40.dp),
            )
          }

          IconButton(
            onClick = {
              clickEvent()
              viewModel.pauseUnpause()
            },
            modifier = Modifier.size(64.dp),
          ) {
            Icon(
              imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
              contentDescription = if (paused) "Play" else "Pause",
              tint = Color.White,
              modifier = Modifier.size(56.dp),
            )
          }

          IconButton(
            onClick = {
              clickEvent()
              if (viewModel.hasNext()) viewModel.playNext()
            },
            enabled = hasNext,
          ) {
            Icon(
              imageVector = Icons.Default.SkipNext,
              contentDescription = "Next",
              tint = Color.White.copy(alpha = if (hasNext) 1f else 0.38f),
              modifier = Modifier.size(40.dp),
            )
          }
        }

        Spacer(modifier = Modifier.weight(1f))
      }
    }
  }
}

private fun formatMxTime(totalSeconds: Int): String {
  val s = totalSeconds.coerceAtLeast(0)
  val hours = s / 3600
  val minutes = s % 3600 / 60
  val seconds = s % 60
  return if (hours > 0) {
    "%d:%02d:%02d".format(hours, minutes, seconds)
  } else {
    "%02d:%02d".format(minutes, seconds)
  }
}

/** Thin seek line with a small round knob, like a minimal media player. */
@Composable
private fun MxThinSeekBar(
  position: Float,
  duration: Float,
  onSeekStart: () -> Unit,
  onSeek: (Float) -> Unit,
  onSeekEnd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  val fraction = dragFraction ?: if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(36.dp)
        .pointerInput(duration) {
          detectTapGestures(
            onTap = { offset ->
              if (duration > 0f) {
                val f = (offset.x / size.width).coerceIn(0f, 1f)
                onSeekStart()
                dragFraction = f
                onSeek(f * duration)
                dragFraction = null
                onSeekEnd()
              }
            },
          )
        }.pointerInput(duration) {
          detectHorizontalDragGestures(
            onDragStart = { offset ->
              if (duration > 0f) {
                val f = (offset.x / size.width).coerceIn(0f, 1f)
                onSeekStart()
                dragFraction = f
                onSeek(f * duration)
              }
            },
            onDragEnd = {
              dragFraction = null
              onSeekEnd()
            },
            onDragCancel = {
              dragFraction = null
              onSeekEnd()
            },
            onHorizontalDrag = { change, _ ->
              change.consume()
              if (duration > 0f) {
                val f = (change.position.x / size.width).coerceIn(0f, 1f)
                dragFraction = f
                onSeek(f * duration)
              }
            },
          )
        },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
      val trackHeight = 4.dp.toPx()
      val startX = trackHeight / 2f
      val endX = size.width - trackHeight / 2f
      val y = size.height / 2f
      val progressX = startX + (endX - startX) * fraction
      drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(startX, y),
        end = Offset(endX, y),
        strokeWidth = trackHeight,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = Color.White,
        start = Offset(startX, y),
        end = Offset(progressX, y),
        strokeWidth = trackHeight,
        cap = StrokeCap.Round,
      )
      drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(progressX, y))
    }
  }
}
