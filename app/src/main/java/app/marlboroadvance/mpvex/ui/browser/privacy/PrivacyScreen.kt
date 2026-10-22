package app.marlboroadvance.mpvex.ui.browser.privacy

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.biometrics.BiometricPrompt
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
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
 * apps, and can only be played from this screen after entering the PIN (or fingerprint).
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
    val fingerprintOn by preferences.privacyFingerprint.collectAsState()
    val gridView by preferences.privacyGrid.collectAsState()
    val sortKey by preferences.privacySort.collectAsState()
    val sortDesc by preferences.privacyDesc.collectAsState()
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var hidden by remember { mutableStateOf<List<HiddenVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val canFingerprint = remember { fingerprintAvailable(context) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<HiddenVideo?>(null) }
    val selecting = selectedIds.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    LaunchedEffect(unlocked) {
      if (unlocked) {
        hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
      }
      loading = false
    }

    val sorted =
      remember(hidden, sortKey, sortDesc) {
        val base =
          when (sortKey) {
            0 -> hidden.sortedBy { it.name.lowercase() }
            1 -> hidden.sortedBy { it.size }
            else -> hidden.sortedBy { File(it.hiddenPath).lastModified() }
          }
        if (sortDesc) base.reversed() else base
      }

    fun reload() {
      scope.launch { hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) } }
    }

    fun toggle(video: HiddenVideo) {
      selectedIds = if (video.id in selectedIds) selectedIds - video.id else selectedIds + video.id
    }

    val setupMode = pinHash.isEmpty()
    val videosMode = !setupMode && unlocked

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text =
                when {
                  setupMode -> "Set PIN"
                  videosMode && selecting -> "${selectedIds.size} selected"
                  videosMode -> "Private Videos"
                  else -> "Privacy"
                },
            )
          },
          navigationIcon = {
            if (selecting) {
              IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
            } else {
              IconButton(onClick = { backstack.removeLastOrNull() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
            }
          },
          actions = {
            if (videosMode && selecting) {
              IconButton(
                onClick = {
                  val chosen = hidden.filter { it.id in selectedIds }
                  selectedIds = emptySet()
                  scope.launch {
                    var ok = 0
                    chosen.forEach { if (PrivacyStore.unhide(context, it)) ok++ }
                    hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
                    Toast.makeText(context, "Restored $ok of ${chosen.size} video(s) to their folders", Toast.LENGTH_SHORT).show()
                  }
                },
              ) { Icon(Icons.Filled.LockOpen, contentDescription = "Unlock") }
              if (selectedIds.size == 1) {
                IconButton(onClick = { renameTarget = hidden.firstOrNull { it.id in selectedIds } }) {
                  Icon(Icons.Filled.Edit, contentDescription = "Rename")
                }
              }
              IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            if (videosMode && !selecting) {
              IconButton(onClick = { preferences.privacyGrid.set(!gridView) }) {
                Icon(
                  if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                  contentDescription = if (gridView) "List view" else "Grid view",
                )
              }
              Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                  Text(
                    "Sort by",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                  )
                  listOf("Name", "Size", "Date added").forEachIndexed { i, label ->
                    DropdownMenuItem(
                      text = { Text(label + if (sortKey == i) (if (sortDesc) "   ↓" else "   ↑") else "") },
                      onClick = {
                        if (sortKey == i) {
                          preferences.privacyDesc.set(!sortDesc)
                        } else {
                          preferences.privacySort.set(i)
                        }
                        menuOpen = false
                      },
                    )
                  }
                  HorizontalDivider()
                  DropdownMenuItem(
                    text = { Text("Change PIN") },
                    onClick = {
                      menuOpen = false
                      showChangePin = true
                    },
                  )
                  if (canFingerprint) {
                    DropdownMenuItem(
                      text = { Text("Unlock with fingerprint") },
                      trailingIcon = { Checkbox(checked = fingerprintOn, onCheckedChange = null) },
                      onClick = {
                        preferences.privacyFingerprint.set(!fingerprintOn)
                        menuOpen = false
                      },
                    )
                  }
                }
              }
            }
          },
        )
      },
      floatingActionButton = {
        if (videosMode && !selecting) {
          FloatingActionButton(onClick = { showImport = true }) { Icon(Icons.Filled.Add, contentDescription = "Import videos") }
        }
      },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when {
          setupMode ->
            SetPinContent(
              canFingerprint = canFingerprint,
              onCreate = { pin, useFingerprint ->
                val salt = newPrivacySalt()
                preferences.privacyPinSalt.set(salt)
                preferences.privacyPinHash.set(hashPrivacyPin(salt, pin))
                preferences.privacyFingerprint.set(useFingerprint)
                unlocked = true
              },
            )
          !unlocked ->
            UnlockContent(
              fingerprintEnabled = fingerprintOn && canFingerprint,
              onSubmit = { pin ->
                val ok = hashPrivacyPin(preferences.privacyPinSalt.get(), pin) == preferences.privacyPinHash.get()
                if (ok) unlocked = true
                ok
              },
              onFingerprintSuccess = { unlocked = true },
            )
          loading -> Text(text = "Loading…", modifier = Modifier.padding(32.dp))
          sorted.isEmpty() -> EmptyContent()
          gridView ->
            LazyVerticalGrid(
              columns = GridCells.Fixed(2),
              modifier = Modifier.fillMaxSize(),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              gridItems(sorted, key = { it.id }) { video ->
                HiddenVideoCard(
                  video = video,
                  selected = video.id in selectedIds,
                  onClick = { if (selecting) toggle(video) else playHidden(context, video) },
                  onLongClick = { toggle(video) },
                )
              }
            }
          else ->
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
              items(sorted, key = { it.id }) { video ->
                HiddenVideoRow(
                  video = video,
                  selected = video.id in selectedIds,
                  onClick = { if (selecting) toggle(video) else playHidden(context, video) },
                  onLongClick = { toggle(video) },
                )
              }
            }
        }
      }
    }

    if (showChangePin) {
      ChangePinDialog(
        verify = { pin -> hashPrivacyPin(preferences.privacyPinSalt.get(), pin) == preferences.privacyPinHash.get() },
        onDismiss = { showChangePin = false },
        onNewPin = { pin ->
          val salt = newPrivacySalt()
          preferences.privacyPinSalt.set(salt)
          preferences.privacyPinHash.set(hashPrivacyPin(salt, pin))
          showChangePin = false
          Toast.makeText(context, "PIN changed", Toast.LENGTH_SHORT).show()
        },
      )
    }
    if (confirmDelete) {
      val chosen = hidden.filter { it.id in selectedIds }
      AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${chosen.size} video(s)?") },
        text = { Text("They will be deleted from your phone for good. This can't be undone.") },
        confirmButton = {
          TextButton(
            onClick = {
              confirmDelete = false
              selectedIds = emptySet()
              scope.launch {
                val n = PrivacyStore.delete(context, chosen)
                hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
                Toast.makeText(context, "$n video(s) deleted", Toast.LENGTH_SHORT).show()
              }
            },
          ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
      )
    }
    renameTarget?.let { target ->
      var newName by remember(target.id) { mutableStateOf(target.name) }
      AlertDialog(
        onDismissRequest = { renameTarget = null },
        title = { Text("Rename") },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
        confirmButton = {
          TextButton(
            onClick = {
              renameTarget = null
              selectedIds = emptySet()
              scope.launch {
                val ok = PrivacyStore.rename(context, target, newName)
                hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
                if (!ok) Toast.makeText(context, "Couldn't rename (name empty or already used)", Toast.LENGTH_SHORT).show()
              }
            },
          ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
      )
    }

    if (showImport) {
      ImportDialog(
        onDismiss = { showImport = false },
        onImport = { paths ->
          showImport = false
          scope.launch {
            val n = PrivacyStore.hide(context, paths)
            hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
            Toast.makeText(context, "$n video(s) moved to Private Videos", Toast.LENGTH_SHORT).show()
          }
        },
      )
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

// ---------------------------------------------------------------------------------------------
// Fingerprint
// ---------------------------------------------------------------------------------------------

private fun fingerprintAvailable(context: Context): Boolean =
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

private fun promptFingerprint(
  context: Context,
  onSuccess: () -> Unit,
) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
  try {
    val executor = ContextCompat.getMainExecutor(context)
    val prompt =
      BiometricPrompt
        .Builder(context)
        .setTitle("Unlock Privacy")
        .setNegativeButton("Use PIN", executor) { _, _ -> }
        .build()
    prompt.authenticate(
      CancellationSignal(),
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
          onSuccess()
        }
      },
    )
  } catch (_: Exception) {
    // no fingerprint enrolled: the PIN still works
  }
}

// ---------------------------------------------------------------------------------------------
// PIN screens (dots + number pad)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PinDots(
  count: Int,
  filled: Int,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(count) { i ->
      Box(
        modifier =
          Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
      )
    }
  }
}

@Composable
private fun KeyCell(
  label: String,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  Box(modifier = modifier.height(84.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Text(label, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Light)
  }
}

@Composable
private fun PinKeypad(
  onDigit: (Char) -> Unit,
  onBackspace: () -> Unit,
  leftSlot: @Composable () -> Unit = {},
) {
  Column(
    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).navigationBarsPadding(),
  ) {
    listOf("123", "456", "789").forEach { row ->
      Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { d -> KeyCell(d.toString(), Modifier.weight(1f)) { onDigit(d) } }
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.weight(1f).height(84.dp), contentAlignment = Alignment.Center) { leftSlot() }
      KeyCell("0", Modifier.weight(1f)) { onDigit('0') }
      Box(modifier = Modifier.weight(1f).height(84.dp).clickable(onClick = onBackspace), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", modifier = Modifier.size(34.dp))
      }
    }
  }
}

@Composable
private fun SetPinContent(
  canFingerprint: Boolean,
  onCreate: (String, Boolean) -> Unit,
) {
  var first by remember { mutableStateOf("") }
  var entry by remember { mutableStateOf("") }
  var error by remember { mutableStateOf(false) }
  var useFingerprint by remember { mutableStateOf(canFingerprint) }
  Column(modifier = Modifier.fillMaxSize()) {
    if (canFingerprint) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { useFingerprint = !useFingerprint }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(checked = useFingerprint, onCheckedChange = { useFingerprint = it })
        Text("Unlock with Fingerprint", style = MaterialTheme.typography.bodyMedium)
      }
    }
    Column(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(if (first.isEmpty()) "Set PIN" else "Confirm PIN", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(36.dp))
      PinDots(count = 4, filled = entry.length)
      Spacer(Modifier.height(24.dp))
      Text(
        text = if (error) "The PINs don't match. Try again." else "Remember it: it can't be reset without clearing the app's data.",
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 32.dp),
      )
    }
    PinKeypad(
      onDigit = { d ->
        if (entry.length < 4) entry += d
        if (entry.length == 4) {
          if (first.isEmpty()) {
            first = entry
            entry = ""
            error = false
          } else if (entry == first) {
            onCreate(entry, useFingerprint)
          } else {
            error = true
            first = ""
            entry = ""
          }
        }
      },
      onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
    )
  }
}

@Composable
private fun UnlockContent(
  fingerprintEnabled: Boolean,
  onSubmit: (String) -> Boolean,
  onFingerprintSuccess: () -> Unit,
) {
  val context = LocalContext.current
  var entry by remember { mutableStateOf("") }
  var wrong by remember { mutableStateOf(false) }
  LaunchedEffect(fingerprintEnabled) {
    if (fingerprintEnabled) promptFingerprint(context, onFingerprintSuccess)
  }
  Column(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text("Enter PIN", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(36.dp))
      PinDots(count = maxOf(4, entry.length), filled = entry.length)
      Spacer(Modifier.height(24.dp))
      Text(
        text = if (wrong) "Wrong PIN" else " ",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
      TextButton(
        onClick = {
          if (!onSubmit(entry)) {
            wrong = true
            entry = ""
          }
        },
        enabled = entry.length >= 4,
      ) { Text("Unlock") }
    }
    PinKeypad(
      onDigit = { d ->
        if (entry.length < 8) {
          entry += d
          wrong = false
          if (entry.length >= 4 && onSubmit(entry)) entry = ""
        }
      },
      onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
      leftSlot = {
        if (fingerprintEnabled) {
          IconButton(onClick = { promptFingerprint(context, onFingerprintSuccess) }) {
            Icon(Icons.Filled.Fingerprint, contentDescription = "Use fingerprint", modifier = Modifier.size(38.dp))
          }
        }
      },
    )
  }
}

@Composable
private fun ChangePinDialog(
  verify: (String) -> Boolean,
  onDismiss: () -> Unit,
  onNewPin: (String) -> Unit,
) {
  var step by remember { mutableStateOf(0) }
  var entry by remember { mutableStateOf("") }
  var first by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
          Text("Change PIN", style = MaterialTheme.typography.titleLarge)
        }
        Column(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            when (step) {
              0 -> "Enter current PIN"
              1 -> "Enter new PIN"
              else -> "Confirm new PIN"
            },
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(36.dp))
          PinDots(count = if (step == 0) maxOf(4, entry.length) else 4, filled = entry.length)
          Spacer(Modifier.height(24.dp))
          Text(text = error ?: " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
          if (step == 0) {
            TextButton(
              onClick = {
                if (verify(entry)) {
                  step = 1
                  entry = ""
                  error = null
                } else {
                  error = "Wrong PIN"
                  entry = ""
                }
              },
              enabled = entry.length >= 4,
            ) { Text("Next") }
          }
        }
        PinKeypad(
          onDigit = { d ->
            when (step) {
              0 ->
                if (entry.length < 8) {
                  entry += d
                  error = null
                  if (entry.length >= 4 && verify(entry)) {
                    step = 1
                    entry = ""
                  }
                }
              1 ->
                if (entry.length < 4) {
                  entry += d
                  if (entry.length == 4) {
                    first = entry
                    entry = ""
                    step = 2
                  }
                }
              else ->
                if (entry.length < 4) {
                  entry += d
                  if (entry.length == 4) {
                    if (entry == first) {
                      onNewPin(entry)
                    } else {
                      error = "The PINs don't match. Try again."
                      first = ""
                      entry = ""
                      step = 1
                    }
                  }
                }
            }
          },
          onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Private videos
// ---------------------------------------------------------------------------------------------

private val thumbCache = LruCache<String, Bitmap>(60)

@Composable
private fun rememberThumb(path: String): Bitmap? {
  val bmp by produceState<Bitmap?>(thumbCache.get(path), path) {
    if (value == null) {
      value =
        withContext(Dispatchers.IO) {
          runCatching {
            val r = MediaMetadataRetriever()
            try {
              r.setDataSource(path)
              r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                Bitmap.createScaledBitmap(it, 320, (320f * it.height / it.width).toInt().coerceAtLeast(1), true)
              }
            } finally {
              r.release()
            }
          }.getOrNull()?.also { thumbCache.put(path, it) }
        }
    }
  }
  return bmp
}

@Composable
private fun Thumb(
  path: String,
  modifier: Modifier,
) {
  val bmp = rememberThumb(path)
  Box(
    modifier = modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center,
  ) {
    if (bmp != null) {
      Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenVideoRow(
  video: HiddenVideo,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val context = LocalContext.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box {
      Thumb(video.hiddenPath, Modifier.width(112.dp).aspectRatio(16f / 9f))
      if (selected) SelectedMark(Modifier.align(Alignment.TopStart))
    }
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
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenVideoCard(
  video: HiddenVideo,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val context = LocalContext.current
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
  ) {
    Box {
      Thumb(video.hiddenPath, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
      if (selected) SelectedMark(Modifier.align(Alignment.TopStart))
    }
    Text(
      text = video.name,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 6.dp),
    )
    Text(
      text = Formatter.formatFileSize(context, video.size),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.outline,
    )
  }
}

@Composable
private fun SelectedMark(modifier: Modifier) {
  Icon(
    Icons.Filled.CheckCircle,
    contentDescription = "Selected",
    tint = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(6.dp).size(26.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
  )
}

@Composable
private fun EmptyContent() {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.Inbox,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outlineVariant,
      modifier = Modifier.size(140.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(
      text = "Only you can watch video in private folder. Import video now.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.outline,
      textAlign = TextAlign.Center,
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Import: pick videos from the phone and move them into Private Videos
// ---------------------------------------------------------------------------------------------

private class ImportItem(
  val name: String,
  val path: String,
  val size: Long,
)

@Composable
private fun ImportDialog(
  onDismiss: () -> Unit,
  onImport: (List<String>) -> Unit,
) {
  val context = LocalContext.current
  var videos by remember { mutableStateOf<List<ImportItem>?>(null) }
  var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(Unit) {
    videos =
      withContext(Dispatchers.IO) {
        val out = mutableListOf<ImportItem>()
        val privateRoot = PrivacyStore.privateDir(context).absolutePath
        runCatching {
          context.contentResolver
            .query(
              MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
              arrayOf(MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE),
              null,
              null,
              MediaStore.Video.Media.DATE_ADDED + " DESC",
            )?.use { c ->
              while (c.moveToNext()) {
                val path = c.getString(1) ?: continue
                if (path.startsWith(privateRoot) || !File(path).isFile) continue
                out.add(ImportItem(c.getString(0) ?: File(path).name, path, c.getLong(2)))
              }
            }
        }
        out
      }
  }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
          Text("Import videos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          TextButton(onClick = { onImport(selected.toList()) }, enabled = selected.isNotEmpty()) {
            Text("Hide (${selected.size})")
          }
        }
        HorizontalDivider()
        val list = videos
        if (list == null) {
          Text("Loading…", modifier = Modifier.padding(24.dp))
        } else if (list.isEmpty()) {
          Text("No videos found", modifier = Modifier.padding(24.dp))
        } else {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.path }) { item ->
              val checked = item.path in selected
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clickable { selected = if (checked) selected - item.path else selected + item.path }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + item.path else selected - item.path })
                Column(modifier = Modifier.weight(1f)) {
                  Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(
                    Formatter.formatFileSize(context, item.size) + "  •  " + (File(item.path).parentFile?.name ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
