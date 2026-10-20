package app.marlboroadvance.mpvex.ui.browser.privacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

/**
 * Privacy: videos you hide live here. They are removed from your folders, the gallery and other
 * apps, and can only be played from this screen after entering the PIN.
 */
@Serializable
object PrivacyScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val preferences = koinInject<BrowserPreferences>()

    val pinHash by preferences.privacyPinHash.collectAsState()
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var hidden by remember { mutableStateOf<List<HiddenVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(unlocked) {
      if (unlocked) {
        hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
      }
      loading = false
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(text = "Privacy", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(onClick = { backstack.removeLastOrNull() }) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
          },
        )
      },
    ) { padding ->
      Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.TopCenter,
      ) {
        when {
          pinHash.isEmpty() -> {
            CreatePinContent(
              onCreate = { pin ->
                val salt = newPrivacySalt()
                preferences.privacyPinSalt.set(salt)
                preferences.privacyPinHash.set(hashPrivacyPin(salt, pin))
                unlocked = true
              },
            )
          }
          !unlocked -> {
            EnterPinContent(
              onSubmit = { pin ->
                val ok = hashPrivacyPin(preferences.privacyPinSalt.get(), pin) == preferences.privacyPinHash.get()
                if (ok) unlocked = true
                ok
              },
            )
          }
          loading -> {
            Text(text = "Loading…", modifier = Modifier.padding(32.dp))
          }
          hidden.isEmpty() -> {
            EmptyContent()
          }
          else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
              items(hidden, key = { it.id }) { video ->
                HiddenVideoRow(
                  video = video,
                  onPlay = { playHidden(context, video) },
                  onUnhide = {
                    scope.launch {
                      val ok = PrivacyStore.unhide(context, video)
                      hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
                      Toast
                        .makeText(
                          context,
                          if (ok) "Restored to its original folder" else "Couldn't restore this video",
                          Toast.LENGTH_SHORT,
                        ).show()
                    }
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun playHidden(
  context: Context,
  video: HiddenVideo,
) {
  val intent = Intent(Intent.ACTION_VIEW, Uri.fromFile(File(video.hiddenPath)))
  intent.setClass(context, PlayerActivity::class.java)
  intent.putExtra("internal_launch", true)
  intent.putExtra("title", video.name)
  context.startActivity(intent)
}

@Composable
private fun HiddenVideoRow(
  video: HiddenVideo,
  onPlay: () -> Unit,
  onUnhide: () -> Unit,
) {
  val context = LocalContext.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onPlay)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.PlayCircle,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(40.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = video.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = Formatter.formatFileSize(context, video.size),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
      )
    }
    IconButton(onClick = onUnhide) {
      Icon(
        imageVector = Icons.Filled.LockOpen,
        contentDescription = "Restore to its folder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptyContent() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outline,
      modifier = Modifier.size(48.dp),
    )
    Text(
      text = "No hidden videos yet",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = "Open a folder, press and hold a video to select it, then tap the hide button. Hidden videos only appear here.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.outline,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun CreatePinContent(onCreate: (String) -> Unit) {
  var pin by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }
  val mismatch = confirm.isNotEmpty() && pin != confirm

  Column(
    modifier = Modifier.fillMaxWidth().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(48.dp),
    )
    Text(
      text = "Create a PIN",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = "Hidden videos can only be opened here, after you enter this PIN. Remember it: it can't be reset without clearing the app's data.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.outline,
      textAlign = TextAlign.Center,
    )
    PinField(value = pin, label = "PIN (4 to 8 digits)", onValueChange = { pin = it })
    PinField(value = confirm, label = "Confirm PIN", onValueChange = { confirm = it })
    if (mismatch) {
      Text(
        text = "The PINs don't match",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    Button(
      onClick = { onCreate(pin) },
      enabled = pin.length >= 4 && pin == confirm,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Create PIN")
    }
  }
}

@Composable
private fun EnterPinContent(onSubmit: (String) -> Boolean) {
  var pin by remember { mutableStateOf("") }
  var wrong by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier.fillMaxWidth().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(48.dp),
    )
    Text(
      text = "Enter your PIN",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    PinField(
      value = pin,
      label = "PIN",
      onValueChange = {
        pin = it
        wrong = false
      },
    )
    if (wrong) {
      Text(
        text = "Wrong PIN",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    Button(
      onClick = {
        if (!onSubmit(pin)) {
          wrong = true
          pin = ""
        }
      },
      enabled = pin.length >= 4,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Unlock")
    }
  }
}
