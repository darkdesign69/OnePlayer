package app.marlboroadvance.mpvex.ui.browser.music

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.media.audiofx.AudioEffect
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.key
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.SpeakerNotes
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Bedtime
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

private data class AlbumItem(
  val key: Long,
  val name: String,
  val artist: String,
  val tracks: List<MusicTrack>,
)

private data class FolderItem(
  val path: String,
  val name: String,
  val tracks: List<MusicTrack>,
)

private data class ArtistItem(
  val name: String,
  val tracks: List<MusicTrack>,
  val albums: Int,
)

private sealed interface Detail {
  val title: String

  data class AlbumD(
    val key: Long,
    override val title: String,
    val artist: String,
  ) : Detail

  data class FolderD(
    val path: String,
    override val title: String,
  ) : Detail

  data class ArtistD(
    override val title: String,
  ) : Detail

  /** kind: fav | history | recent | playlist */
  data class ListD(
    override val title: String,
    val kind: String,
  ) : Detail
}

private class SongActions(
  val playNext: (MusicTrack) -> Unit,
  val addToQueue: (MusicTrack) -> Unit,
  val addToPlaylist: (MusicTrack) -> Unit,
  val goAlbum: (MusicTrack) -> Unit,
  val goArtist: (MusicTrack) -> Unit,
  val share: (MusicTrack) -> Unit,
  val info: (MusicTrack) -> Unit,
)

private val sortNames = listOf("Title", "Artist", "Album", "Date added", "Duration")

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

/** Retro-Music-Player style music library + now playing, running inside OnePlayer. */
@Serializable
object MusicScreen : Screen {
  @Composable
  override fun Content() {
    MusicSettings.init(LocalContext.current)
    MusicPanelTheme { MusicScreenBody() }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun MusicScreenBody() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    MusicController.init(context)
    MusicSettings.init(context)

    val permission =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
      } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
      }
    var granted by remember {
      mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(granted, reloadKey, MusicSettings.minDurationSec, MusicSettings.excludedFolders, MusicController.libraryVersion) {
      if (granted) {
        loading = true
        tracks = withContext(Dispatchers.IO) { loadMusicTracks(context) }
      }
      loading = false
    }

    LaunchedEffect(tracks) {
      if (tracks.isNotEmpty()) MusicController.restoreQueue(tracks.associateBy { it.id })
    }
    DisposableEffect(Unit) {
      onDispose { MusicController.saveQueueState() }
    }

    var tab by remember { mutableIntStateOf(MusicSettings.startTab()) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(tab) {
      MusicSettings.lastTab = tab
      MusicSettings.save()
    }
    LaunchedEffect(MusicSettings.tabsMask) {
      if (!MusicSettings.isTabVisible(tab)) tab = MusicSettings.firstVisibleTab()
    }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var showNow by remember { mutableStateOf(MusicNav.takeOpenNow()) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableIntStateOf(MusicSettings.sortBy) }
    var sortDesc by remember { mutableStateOf(MusicSettings.sortDesc) }
    LaunchedEffect(sortBy, sortDesc) {
      MusicSettings.sortBy = sortBy
      MusicSettings.sortDesc = sortDesc
      MusicSettings.save()
    }
    var sortMenu by remember { mutableStateOf(false) }

    var addTarget by remember { mutableStateOf<MusicTrack?>(null) }
    var addMany by remember { mutableStateOf<List<MusicTrack>?>(null) }
    var infoTarget by remember { mutableStateOf<MusicTrack?>(null) }
    var newPlaylistFor by remember { mutableStateOf<Long?>(null) }
    var newPlaylistDialog by remember { mutableStateOf(false) }

    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val albums = remember(tracks) { buildAlbums(tracks) }
    val artists = remember(tracks, MusicSettings.multiArtists, MusicSettings.artistSeparators) { buildArtists(tracks) }
    val folders =
      remember(tracks) {
        tracks
          .groupBy { it.path.substringBeforeLast('/', "") }
          .filter { it.key.isNotEmpty() }
          .map { (path, v) -> FolderItem(path, path.substringAfterLast('/'), v) }
          .sortedBy { it.name.lowercase() }
      }
    val sortedSongs =
      remember(tracks, sortBy, sortDesc) {
        val cmp: Comparator<MusicTrack> =
          when (sortBy) {
            1 -> compareBy<MusicTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            2 -> compareBy<MusicTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
            3 -> compareBy<MusicTrack> { it.dateAdded }
            4 -> compareBy<MusicTrack> { it.durationMs }
            else -> compareBy<MusicTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
          }
        val s = tracks.sortedWith(cmp)
        if (sortDesc) s.reversed() else s
      }

    fun scanPhone() {
      scanning = true
      val root = android.os.Environment.getExternalStorageDirectory().absolutePath
      MediaScannerConnection.scanFile(context, arrayOf(root), null) { _, _ ->
        reloadKey += 1
        scanning = false
      }
    }

    fun startPlay(
      list: List<MusicTrack>,
      i: Int,
      shuffle: Boolean = false,
    ) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
      ) {
        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
      if (shuffle) MusicController.shuffleAll(list) else MusicController.play(list, i, MusicController.shuffle && MusicSettings.keepShuffle)
    }

    fun openNow() {
      if (MusicSettings.openNowOnPlay) showNow = true
    }

    fun detailTracks(d: Detail): List<MusicTrack> =
      when (d) {
        is Detail.AlbumD -> albums.firstOrNull { it.key == d.key }?.tracks.orEmpty()
        is Detail.ArtistD -> artists.firstOrNull { it.name == d.title }?.tracks.orEmpty()
        is Detail.FolderD -> tracks.filter { it.path.substringBeforeLast('/', "") == d.path }
        is Detail.ListD ->
          when (d.kind) {
            "fav" -> MusicController.favorites.mapNotNull { byId[it] }
            "history" -> MusicController.history.mapNotNull { byId[it] }
            "recent" -> lastAdded(tracks).take(200)
            else -> MusicController.playlists[d.title].orEmpty().mapNotNull { byId[it] }
          }
      }

    val addScope = rememberCoroutineScope()
    val addFilesLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
          addScope.launch {
            val list = withContext(Dispatchers.IO) { uris.mapNotNull { trackFromUri(context, it) } }
            if (list.isEmpty()) {
              Toast.makeText(context, "Could not read those files", Toast.LENGTH_SHORT).show()
            } else if (MusicController.queue.isEmpty()) {
              MusicController.play(list, 0, false)
              openNow()
            } else {
              list.forEach { MusicController.addToQueue(it) }
              Toast.makeText(context, "Added ${list.size} to the queue", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }

    val actions =
      SongActions(
        playNext = {
          MusicController.playNext(it)
          Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
        },
        addToQueue = {
          MusicController.addToQueue(it)
          Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
        },
        addToPlaylist = { addTarget = it },
        goAlbum = {
          detail = Detail.AlbumD(if (it.albumId > 0) it.albumId else -it.id, it.album, it.artist)
          showNow = false
          searching = false
        },
        goArtist = {
          detail = Detail.ArtistD(if (MusicSettings.multiArtists) splitArtists(it.artist).first() else it.artist)
          showNow = false
          searching = false
        },
        share = { shareTrack(context, it) },
        info = { infoTarget = it },
      )

    BackHandler(enabled = showNow || detail != null || searching || showSettings) {
      when {
        showNow -> showNow = false
        showSettings -> {
          if (settingsPage >= 0) settingsPage = -1 else showSettings = false
        }
        searching -> {
          searching = false
          query = ""
        }
        else -> detail = null
      }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      Scaffold(
        topBar = {
          TopAppBar(
            title = {
              if (searching) {
                TextField(
                  value = query,
                  onValueChange = { query = it },
                  placeholder = { Text("Search songs, albums, artists") },
                  singleLine = true,
                  colors =
                    TextFieldDefaults.colors(
                      focusedContainerColor = Color.Transparent,
                      unfocusedContainerColor = Color.Transparent,
                      focusedIndicatorColor = Color.Transparent,
                      unfocusedIndicatorColor = Color.Transparent,
                    ),
                  modifier = Modifier.fillMaxWidth(),
                )
              } else {
                Text(text = if (showSettings) "" else (detail?.title ?: "Music"), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            },
            navigationIcon = {
              IconButton(
                onClick = {
                  when {
                    showSettings -> {
          if (settingsPage >= 0) settingsPage = -1 else showSettings = false
        }
                    searching -> {
                      searching = false
                      query = ""
                    }
                    detail != null -> detail = null
                    else -> backstack.removeLastOrNull()
                  }
                },
              ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
            actions = {
              if (!searching && detail == null && !showSettings) {
                IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Search") }
                IconButton(enabled = granted && tracks.isNotEmpty(), onClick = { startPlay(tracks, 0, true); openNow() }) {
                  Icon(Icons.Filled.Shuffle, "Shuffle all")
                }
                if (tab == 1) {
                  Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = popupColor()) {
                      sortNames.forEachIndexed { i, n ->
                        DropdownMenuItem(
                          text = { Text(n + if (i == sortBy) (if (sortDesc) "  ↓" else "  ↑") else "") },
                          onClick = {
                            if (sortBy == i) sortDesc = !sortDesc else { sortBy = i; sortDesc = false }
                            sortMenu = false
                          },
                        )
                      }
                    }
                  }
                }
                IconButton(onClick = { addFilesLauncher.launch(arrayOf("audio/*")) }) { Icon(Icons.Filled.Add, "Add files") }
                IconButton(onClick = { settingsPage = -1; showSettings = true }) { Icon(Icons.Filled.Settings, "Music settings") }
              } else if (searching && query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
              }
            },
          )
        },
        bottomBar = {
          Column {
            if (MusicController.current != null) SolidThemed { MiniPlayer(onOpen = { showNow = true }) }
            NavigationBar {
              val items =
                listOf(
                  "Home" to Icons.Filled.Home,
                  "Songs" to Icons.Filled.MusicNote,
                  "Albums" to Icons.Filled.Album,
                  "Artists" to Icons.Filled.Person,
                  "Playlists" to Icons.AutoMirrored.Filled.QueueMusic,
                  "Folders" to Icons.Filled.Folder,
                )
              MusicSettings.tabOrderList().forEach { i ->
                val (label, icon) = items[i]
                if (MusicSettings.isTabVisible(i)) {
                  val labelText: @Composable () -> Unit = { Text(label, maxLines = 1) }
                  NavigationBarItem(
                    selected = tab == i && detail == null,
                    onClick = {
                      tab = i
                      detail = null
                      searching = false
                      query = ""
                    },
                    icon = { Icon(icon, contentDescription = label) },
                    label = if (MusicSettings.tabLabels == 2) null else labelText,
                    alwaysShowLabel = MusicSettings.tabLabels == 0,
                  )
                }
              }
            }
          }
        },
      ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
          val d = detail
          val q = query.trim()
          when {
            showSettings ->
              MusicSettingsHost(
                page = settingsPage,
                tracks = tracks,
                onPage = { settingsPage = it },
                onScan = { scanPhone(); showSettings = false },
                onClearHistory = {
                  MusicController.clearHistory()
                  Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                },
              )
            !granted ->
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
              ) {
                Text("Allow access to your music files to list them here.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { permissionLauncher.launch(permission) }) { Text("Allow access") }
              }
            loading || scanning -> CircularProgressIndicator()
            tracks.isEmpty() ->
              Text("No music found on this device", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            searching && q.isNotEmpty() -> {
              val songs = tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
              if (songs.isEmpty()) {
                Text("No results", color = MaterialTheme.colorScheme.outline)
              } else {
                SongList(songs, actions, onPlay = { i -> startPlay(songs, i); openNow() })
              }
            }
            d != null -> {
              val list = detailTracks(d)
              DetailView(
                detail = d,
                songs = list,
                actions = actions,
                onPlay = { i -> startPlay(list, i); openNow() },
                onShuffle = { startPlay(list, 0, true); openNow() },
                onRemoveFromPlaylist = if (d is Detail.ListD && d.kind == "playlist") ({ t: MusicTrack -> MusicController.removeFromPlaylist(d.title, t.id) }) else null,
                onClearHistory = if (d is Detail.ListD && d.kind == "history") ({ MusicController.clearHistory() }) else null,
              )
            }
            tab == 0 ->
              HomeTab(
                tracks = tracks,
                albums = albums,
                byId = byId,
                onShuffle = { startPlay(tracks, 0, true); openNow() },
                onOpen = { detail = it },
                onPlayTrack = { list, i -> startPlay(list, i); openNow() },
                onAlbum = { detail = Detail.AlbumD(it.key, it.name, it.artist) },
              )
            tab == 1 -> SongList(sortedSongs, actions, onPlay = { i -> startPlay(sortedSongs, i); openNow() })
            tab == 2 ->
              LazyVerticalGrid(
                columns = GridCells.Fixed(MusicSettings.gridColumns.coerceIn(2, 4)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                items(albums, key = { it.key }) { a ->
                  Column(modifier = Modifier.clickable { detail = Detail.AlbumD(a.key, a.name, a.artist) }) {
                    TrackArt(a.tracks.first(), 400, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(18.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(a.name, fontWeight = MusicFont.medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            tab == 3 ->
              LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(artists, key = { it.name }) { a ->
                  Row(
                    modifier = Modifier.fillMaxWidth().clickable { detail = Detail.ArtistD(a.name) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                  ) {
                    TrackArt(a.tracks.first(), 160, Modifier.size(52.dp), CircleShape)
                    Column {
                      Text(a.name, fontWeight = MusicFont.medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("${a.albums} albums  •  ${a.tracks.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                  }
                }
              }
            tab == 5 ->
              FoldersTab(
                folders = folders,
                onOpen = { detail = Detail.FolderD(it.path, it.name) },
                onPlay = { f ->
                  startPlay(f.tracks, 0)
                  openNow()
                },
                onPlayNext = { f ->
                  if (MusicController.queue.isEmpty()) startPlay(f.tracks, 0) else f.tracks.reversed().forEach { MusicController.playNext(it) }
                  Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                },
                onQueue = { f ->
                  f.tracks.forEach { MusicController.addToQueue(it) }
                  Toast.makeText(context, "Added to the queue", Toast.LENGTH_SHORT).show()
                },
                onAddToPlaylist = { f -> addMany = f.tracks },
              )
            else ->
              PlaylistsTab(
                onOpen = { detail = it },
                onNew = { newPlaylistDialog = true },
              )
          }
        }
      }

      AnimatedVisibility(
        visible = showNow && MusicController.current != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
      ) {
        SolidThemed { NowPlaying(onClose = { showNow = false }, actions = actions) }
      }
    }

    // ---- dialogs ----
    addTarget?.let { t ->
      AlertDialog(
      containerColor = popupColor(),
        onDismissRequest = { addTarget = null },
        title = { Text("Add to playlist") },
        text = {
          Column {
            TextButton(onClick = { newPlaylistFor = t.id; addTarget = null }) { Text("+ New playlist") }
            MusicController.playlists.keys.forEach { name ->
              TextButton(
                onClick = {
                  MusicController.addToPlaylist(name, t.id)
                  addTarget = null
                },
              ) { Text(name) }
            }
          }
        },
        confirmButton = { TextButton(onClick = { addTarget = null }) { Text("Cancel") } },
      )
    }
    if (newPlaylistDialog || newPlaylistFor != null) {
      var name by remember { mutableStateOf("") }
      AlertDialog(
      containerColor = popupColor(),
        onDismissRequest = { newPlaylistDialog = false; newPlaylistFor = null },
        title = { Text("New playlist") },
        text = { TextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Playlist name") }) },
        confirmButton = {
          TextButton(
            onClick = {
              MusicController.createPlaylist(name, newPlaylistFor)
              newPlaylistDialog = false
              newPlaylistFor = null
            },
          ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { newPlaylistDialog = false; newPlaylistFor = null }) { Text("Cancel") } },
      )
    }
    addMany?.let { list ->
      var newName by remember(list) { mutableStateOf("") }
      AlertDialog(
        containerColor = popupColor(),
        onDismissRequest = { addMany = null },
        title = { Text("Add ${list.size} songs to playlist") },
        text = {
          Column {
            TextField(value = newName, onValueChange = { newName = it }, singleLine = true, placeholder = { Text("New playlist name") })
            TextButton(
              enabled = newName.isNotBlank(),
              onClick = {
                val n = newName.trim()
                MusicController.createPlaylist(n)
                list.forEach { MusicController.addToPlaylist(n, it.id) }
                addMany = null
              },
            ) { Text("Create and add") }
            MusicController.playlists.keys.forEach { name ->
              TextButton(
                onClick = {
                  list.forEach { MusicController.addToPlaylist(name, it.id) }
                  addMany = null
                },
              ) { Text(name) }
            }
          }
        },
        confirmButton = { TextButton(onClick = { addMany = null }) { Text("Cancel") } },
      )
    }
    infoTarget?.let { t ->
      AlertDialog(
      containerColor = popupColor(),
        onDismissRequest = { infoTarget = null },
        title = { Text("Details") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Title: ${t.title}")
            Text("Artist: ${t.artist}")
            Text("Album: ${t.album}")
            Text("Duration: ${formatTrackTime(t.durationMs)}")
            Text("Path: ${t.path}")
          }
        },
        confirmButton = { TextButton(onClick = { infoTarget = null }) { Text("OK") } },
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeTab(
  tracks: List<MusicTrack>,
  albums: List<AlbumItem>,
  byId: Map<Long, MusicTrack>,
  onShuffle: () -> Unit,
  onOpen: (Detail) -> Unit,
  onPlayTrack: (List<MusicTrack>, Int) -> Unit,
  onAlbum: (AlbumItem) -> Unit,
) {
  val recent = remember(tracks, MusicSettings.lastAddedDays) { lastAdded(tracks).take(20) }
  val played = MusicController.history.mapNotNull { byId[it] }.take(20)
  val randomAlbums = remember(albums) { albums.shuffled().take(20) }
  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
          Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Shuffle")
        }
        FilledTonalButton(onClick = { onOpen(Detail.ListD("Favorites", "fav")) }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Favorites")
        }
        FilledTonalButton(onClick = { onOpen(Detail.ListD("History", "history")) }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Filled.History, null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("History")
        }
      }
    }
    if (played.isNotEmpty() && MusicSettings.homeRecent) {
      item { SectionTitle("Recently played", null) }
      item { TrackRow(played) { i -> onPlayTrack(played, i) } }
    }
    if (MusicSettings.homeLastAdded) {
      item { SectionTitle("Last added", if (MusicSettings.seeAll) "See all" else null) { onOpen(Detail.ListD("Last added", "recent")) } }
      item { TrackRow(recent) { i -> onPlayTrack(recent, i) } }
    }
    if (randomAlbums.isNotEmpty() && MusicSettings.homeAlbums) {
      item { SectionTitle("Albums", null) }
      item {
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(randomAlbums, key = { it.key }) { a ->
            Column(modifier = Modifier.width(140.dp).clickable { onAlbum(a) }) {
              TrackArt(a.tracks.first(), 300, Modifier.size(140.dp), RoundedCornerShape(18.dp))
              Spacer(Modifier.height(6.dp))
              Text(a.name, fontWeight = MusicFont.medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(a.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(
  text: String,
  action: String?,
  onAction: () -> Unit = {},
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    if (action != null) TextButton(onClick = onAction) { Text(action) }
  }
}

@Composable
private fun TrackRow(
  list: List<MusicTrack>,
  onClick: (Int) -> Unit,
) {
  LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    itemsIndexed(list) { i, t ->
      Column(modifier = Modifier.width(140.dp).clickable { onClick(i) }) {
        TrackArt(t, 300, Modifier.size(140.dp), RoundedCornerShape(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(t.title, fontWeight = MusicFont.medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun PlaylistsTab(
  onOpen: (Detail) -> Unit,
  onNew: () -> Unit,
) {
  var menuFor by remember { mutableStateOf<String?>(null) }
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    item {
      PlaylistRow("Favorites", "${MusicController.favorites.size} songs", Icons.Filled.Favorite) { onOpen(Detail.ListD("Favorites", "fav")) }
    }
    item {
      PlaylistRow("History", "${MusicController.history.size} songs", Icons.Filled.History) { onOpen(Detail.ListD("History", "history")) }
    }
    item {
      PlaylistRow("Last added", "Newest songs", Icons.Filled.LibraryMusic) { onOpen(Detail.ListD("Last added", "recent")) }
    }
    item {
      Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Filled.Add, null)
          Spacer(Modifier.width(8.dp))
          Text("New playlist")
        }
      }
    }
    items(MusicController.playlists.keys.toList(), key = { it }) { name ->
      Box {
        PlaylistRow(name, "${MusicController.playlists[name].orEmpty().size} songs", Icons.AutoMirrored.Filled.QueueMusic, onLong = { menuFor = name }) {
          onOpen(Detail.ListD(name, "playlist"))
        }
        DropdownMenu(expanded = menuFor == name, onDismissRequest = { menuFor = null }, containerColor = popupColor()) {
          DropdownMenuItem(
            text = { Text("Delete playlist") },
            leadingIcon = { Icon(Icons.Filled.Delete, null) },
            onClick = {
              MusicController.deletePlaylist(name)
              menuFor = null
            },
          )
        }
      }
    }
  }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onLong: (() -> Unit)? = null,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .then(
          if (onLong != null) {
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLong)
          } else {
            Modifier.clickable(onClick = onClick)
          },
        ).padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
      contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    Column {
      Text(title, fontWeight = MusicFont.medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
  }
}

@Composable
private fun DetailView(
  detail: Detail,
  songs: List<MusicTrack>,
  actions: SongActions,
  onPlay: (Int) -> Unit,
  onShuffle: () -> Unit,
  onRemoveFromPlaylist: ((MusicTrack) -> Unit)?,
  onClearHistory: (() -> Unit)?,
) {
  val subtitle =
    when (detail) {
      is Detail.AlbumD -> "${detail.artist}  •  ${songs.size} songs"
      else -> "${songs.size} songs"
    }
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    item {
      Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (songs.isNotEmpty()) {
          TrackArt(songs.first(), 500, Modifier.size(200.dp), if (detail is Detail.ArtistD) CircleShape else RoundedCornerShape(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(enabled = songs.isNotEmpty(), onClick = { onPlay(0) }) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play")
          }
          FilledTonalButton(enabled = songs.isNotEmpty(), onClick = onShuffle) {
            Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Shuffle")
          }
          if (onClearHistory != null) OutlinedButton(onClick = onClearHistory) { Text("Clear") }
        }
      }
    }
    if (songs.isEmpty()) {
      item { Text("Nothing here yet", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.outline) }
    }
    itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
      SongRow(t, actions, onClick = { onPlay(i) }, extra = onRemoveFromPlaylist?.let { r -> { r(t) } })
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Rows / lists
// ---------------------------------------------------------------------------------------------

@Composable
private fun SongList(
  songs: List<MusicTrack>,
  actions: SongActions,
  onPlay: (Int) -> Unit,
) {
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    itemsIndexed(songs, key = { _, t -> t.id }) { i, t -> SongRow(t, actions, onClick = { onPlay(i) }) }
  }
}

@Composable
private fun SongRow(
  track: MusicTrack,
  actions: SongActions,
  onClick: () -> Unit,
  extra: (() -> Unit)? = null,
) {
  var menu by remember { mutableStateOf(false) }
  var showTags by remember { mutableStateOf(false) }
  val isCurrent = MusicController.current?.id == track.id
  val rowHighlight = accentPalette.getOrNull(MusicSettings.highlightIdx - 1) ?: MaterialTheme.colorScheme.primary
  val rowShade = lerp(solidSurface(), rowHighlight.copy(alpha = 1f), MusicSettings.highlightAlpha / 100f)
  // On the highlighted row the text is black or white, whichever reads better on the highlight colour
  val color =
    if (isCurrent) {
      if (rowShade.luminance() > 0.5f) Color(0xFF121212) else Color.White
    } else {
      MaterialTheme.colorScheme.onSurface
    }
  val subColor = if (isCurrent) color.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .then(
          if (isCurrent) {
            Modifier.background((accentPalette.getOrNull(MusicSettings.highlightIdx - 1) ?: MaterialTheme.colorScheme.primary).copy(alpha = MusicSettings.highlightAlpha / 100f))
          } else {
            Modifier
          },
        ).clickable(onClick = onClick)
        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (MusicSettings.showSongArt) TrackArt(track, 140, Modifier.size(48.dp), RoundedCornerShape(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = MusicFont.medium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        if (MusicSettings.showSongDuration) "${track.artist}  •  ${formatTrackTime(track.durationMs)}" else track.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = subColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
  }
  if (menu) {
    val fav = track.id in MusicController.favorites
    val ctx = LocalContext.current
    ActionSheet(
      title = track.title,
      subtitle = track.artist,
      leading = { TrackArt(track, 140, Modifier.size(56.dp), RoundedCornerShape(12.dp)) },
      items =
        buildList {
          add(SheetItem("Play", Icons.Filled.PlayArrow) { onClick() })
          add(SheetItem("Play next", Icons.Filled.SkipNext) { actions.playNext(track) })
          add(SheetItem("Add to playing queue", Icons.Filled.AddToQueue) { actions.addToQueue(track) })
          add(SheetItem("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { actions.addToPlaylist(track) })
          add(
            SheetItem(if (fav) "Remove from favorites" else "Add to favorites", if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
              MusicController.toggleFavorite(track.id)
            },
          )
          if (extra != null) add(SheetItem("Remove from playlist", Icons.Filled.Delete) { extra() })
          add(SheetItem("Go to album", Icons.Filled.Album, divider = true) { actions.goAlbum(track) })
          add(SheetItem("Go to artist", Icons.Filled.Person) { actions.goArtist(track) })
          add(SheetItem("Tag editor", Icons.Filled.Edit, divider = true) { showTags = true })
          add(SheetItem("Details", Icons.Filled.Info) { actions.info(track) })
          add(SheetItem("Share", Icons.Filled.Share) { actions.share(track) })
          val folder = track.path.substringBeforeLast('/', "")
          if (folder.isNotEmpty()) add(SheetItem("Blacklist", Icons.Filled.VisibilityOff, divider = true) { blacklistFolder(ctx, folder) })
        },
      onDismiss = { menu = false },
    )
  }
  if (showTags) TagEditorDialog(track) { showTags = false }
}

/** Full-screen Tag editor: cover, title, album, artist, composer, genres, year, track and disc numbers, lyrics. */
@Composable
internal fun TagEditorDialog(
  track: MusicTrack,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    TagEditorScreen(track, onDismiss)
  }
}

@Composable
private fun TagEditorScreen(
  track: MusicTrack,
  onClose: () -> Unit,
) {
  val ctx = LocalContext.current
  val saved = remember(track.id) { MusicTagEdits.get(ctx, track.id) }
  fun v(key: String) = saved.optString(key, "")
  var title by remember(track.id) { mutableStateOf(track.title) }
  var album by remember(track.id) { mutableStateOf(track.album) }
  var artist by remember(track.id) { mutableStateOf(track.artist) }
  var albumArtist by remember(track.id) { mutableStateOf(v("albumArtist")) }
  var composer by remember(track.id) { mutableStateOf(v("composer")) }
  var genres by remember(track.id) { mutableStateOf(v("genres")) }
  var year by remember(track.id) { mutableStateOf(v("year")) }
  var trackNo by remember(track.id) { mutableStateOf(v("trackNo")) }
  var trackTotal by remember(track.id) { mutableStateOf(v("trackTotal")) }
  var discNo by remember(track.id) { mutableStateOf(v("discNo")) }
  var discTotal by remember(track.id) { mutableStateOf(v("discTotal")) }
  var lyrics by remember(track.id) { mutableStateOf("") }
  var lyricsLoaded by remember(track.id) { mutableStateOf("") }
  var coverTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(track.id) {
    val t = withContext(Dispatchers.IO) { LyricsStore.load(ctx, track) } ?: ""
    lyrics = t
    lyricsLoaded = t
  }
  val pick =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) {
        if (MusicTagEdits.setCover(ctx, track.id, uri)) {
          coverTick++
          MusicController.libraryVersion++
        } else {
          Toast.makeText(ctx, "Could not use that picture", Toast.LENGTH_SHORT).show()
        }
      }
    }
  val num = KeyboardOptions(keyboardType = KeyboardType.Number)

  @Composable
  fun field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    single: Boolean = true,
    onChange: (String) -> Unit,
  ) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = single,
    keyboardOptions = keyboard,
    modifier = modifier,
  )

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Box(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
          Text("Tag editor", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
              key(coverTick) { TrackArt(track, 400, Modifier.size(160.dp), RoundedCornerShape(16.dp)) }
              Row(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(onClick = { pick.launch("image/*") }, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Edit, "Change cover") }
                FilledTonalIconButton(
                  onClick = {
                    MusicTagEdits.removeCover(ctx, track.id)
                    coverTick++
                    MusicController.libraryVersion++
                  },
                  modifier = Modifier.size(44.dp),
                ) { Icon(Icons.Filled.Delete, "Remove cover") }
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
              Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
          }
          Spacer(Modifier.height(4.dp))
          field("Title", title) { title = it }
          field("Album", album) { album = it }
          field("Artist", artist) { artist = it }
          field("Album artist", albumArtist) { albumArtist = it }
          field("Composer", composer) { composer = it }
          field("Genres", genres) { genres = it }
          field("Year", year, keyboard = num) { year = it }
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            field("Track number", trackNo, Modifier.weight(1f), num) { trackNo = it }
            field("Track total", trackTotal, Modifier.weight(1f), num) { trackTotal = it }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            field("Disc number", discNo, Modifier.weight(1f), num) { discNo = it }
            field("Disc total", discTotal, Modifier.weight(1f), num) { discTotal = it }
          }
          OutlinedTextField(
            value = lyrics,
            onValueChange = { lyrics = it },
            label = { Text("Lyrics") },
            minLines = 4,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(96.dp))
        }
      }
      FloatingActionButton(
        onClick = {
          val t = title.trim().ifBlank { track.title }
          val a = artist.trim().ifBlank { track.artist }
          val al = album.trim().ifBlank { track.album }
          MusicTagEdits.save(
            ctx,
            track,
            mapOf(
              "title" to t,
              "artist" to a,
              "album" to al,
              "albumArtist" to albumArtist.trim(),
              "composer" to composer.trim(),
              "genres" to genres.trim(),
              "year" to year.trim(),
              "trackNo" to trackNo.trim(),
              "trackTotal" to trackTotal.trim(),
              "discNo" to discNo.trim(),
              "discTotal" to discTotal.trim(),
            ),
          )
          if (lyrics != lyricsLoaded) LyricsStore.save(ctx, track, lyrics)
          MusicController.applyTagEdit(track.copy(title = t, artist = a, album = al))
          Toast.makeText(ctx, "Tags saved", Toast.LENGTH_SHORT).show()
          onClose()
        },
        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(20.dp),
      ) { Icon(Icons.Filled.Save, "Save") }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Mini player + Now playing
// ---------------------------------------------------------------------------------------------

@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
  val t = MusicController.current ?: return
  var pos by remember { mutableIntStateOf(0) }
  LaunchedEffect(MusicController.index, MusicController.isPlaying) {
    while (true) {
      pos = MusicController.position()
      delay(500)
    }
  }
  val miniCtx = LocalContext.current
  val miniBase = solidSurface()
  val miniArt by produceState<Bitmap?>(null, t.id) { value = withContext(Dispatchers.IO) { loadArt(miniCtx, t, 160) } }
  Surface(
    color = miniBase,
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
        .pointerInput(Unit) {
          // swipe the mini player down to close it and stop the music
          var dy = 0f
          detectVerticalDragGestures(
            onDragStart = { dy = 0f },
            onDragEnd = { if (dy > 70f) MusicController.stopAll() },
          ) { _, d -> dy += d }
        },
  ) {
    Box {
    val ma = miniArt
    if (ma != null) {
      Image(bitmap = ma.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().blur(28.dp))
      Box(modifier = Modifier.matchParentSize().background(miniBase.copy(alpha = 0.62f)))
    }
    Column {
      LinearProgressIndicator(
        progress = { if (t.durationMs > 0) (pos.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(2.dp),
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TrackArt(t, 140, Modifier.size(44.dp), RoundedCornerShape(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(t.title, fontWeight = MusicFont.medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (MusicSettings.miniControls == 2) {
          IconButton(onClick = { MusicController.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
        }
        IconButton(onClick = { MusicController.togglePlay() }) {
          Icon(if (MusicController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
        }
        if (MusicSettings.miniControls != 1) {
          IconButton(onClick = { MusicController.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
        }
      }
    }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(
  onClose: () -> Unit,
  actions: SongActions,
) {
  val context = LocalContext.current
  val t = MusicController.current ?: return
  var pos by remember { mutableIntStateOf(0) }
  var dragging by remember { mutableStateOf(false) }
  var dragValue by remember { mutableStateOf(0f) }
  var showQueue by remember { mutableStateOf(false) }
  var showSleep by remember { mutableStateOf(false) }
  var showSpeed by remember { mutableStateOf(false) }
  var menu by remember { mutableStateOf(false) }
  var showLyrics by remember { mutableStateOf(false) }
  val info by produceState("", t.id) { value = withContext(Dispatchers.IO) { loadSongInfo(context, t) } }
  fun clock(ms: Long): String {
    val sec = (ms / 1000).toInt().coerceAtLeast(0)
    return "%02d:%02d".format(sec / 60, sec % 60)
  }
  var showEditor by remember { mutableStateOf(false) }
  var showTags by remember { mutableStateOf(false) }
  val swipeDir = remember { mutableIntStateOf(1) }
  val dragX = remember { Animatable(0f) }
  val swipeScope = rememberCoroutineScope()
  LaunchedEffect(t.id) { swipeDir.intValue = 1 }

  LaunchedEffect(MusicController.index, MusicController.isPlaying) {
    while (true) {
      if (!dragging) pos = MusicController.position()
      delay(300)
    }
  }
  val accent by produceState(Color.Transparent, t.id) {
    value =
      withContext(Dispatchers.IO) {
        val b = loadArt(context, t, 64)
        if (b != null) Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)) else Color.Transparent
      }
  }
  val animAccent by animateColorAsState(accent, label = "accent")
  val view = LocalView.current
  DisposableEffect(MusicSettings.keepScreenOn) {
    view.keepScreenOn = MusicSettings.keepScreenOn
    onDispose { view.keepScreenOn = false }
  }
  val fav = t.id in MusicController.favorites
  val duration = t.durationMs.coerceAtLeast(1L).toFloat()
  val baseColor = solidSurface()
  val useBlur = MusicSettings.nowPlayingBg == 3 || MusicSettings.nowPlayingBg == 4
  val blurBmp by produceState<Bitmap?>(null, t.id, MusicSettings.nowPlayingBg) {
    value = if (useBlur) withContext(Dispatchers.IO) { loadArt(context, t, 256) } else null
  }
  val coverFraction =
    when (MusicSettings.coverSize) {
      1 -> 0.82f
      2 -> 0.65f
      else -> 1f
    }

  val npCs = MaterialTheme.colorScheme
  val npApprox =
    when (MusicSettings.nowPlayingBg) {
      1 -> animAccent.copy(alpha = 0.75f).compositeOver(baseColor)
      2 -> lerp(animAccent.copy(alpha = 0.6f).compositeOver(baseColor), baseColor, 0.5f)
      3 -> animAccent.copy(alpha = 0.45f).compositeOver(baseColor)
      4 -> lerp(animAccent.copy(alpha = 0.6f).compositeOver(baseColor), baseColor, 0.5f)
      5 -> animAccent.copy(alpha = 0.3f).compositeOver(baseColor)
      6 -> lerp(baseColor, npCs.primary, 0.25f)
      else -> baseColor
    }
  val npOn = if (npApprox.luminance() > 0.5f) Color(0xFF121212) else Color.White
  val npScheme = npCs.copy(onSurface = npOn, onSurfaceVariant = npOn.copy(alpha = 0.78f), outline = npOn.copy(alpha = 0.65f))
  MaterialTheme(colorScheme = npScheme) {
  Surface(modifier = Modifier.fillMaxSize(), color = baseColor, contentColor = npOn) {
    val artBg = blurBmp
    Box(modifier = Modifier.fillMaxSize()) {
    if (useBlur && artBg != null) {
      Image(
        bitmap = artBg.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur((MusicSettings.blurAmount.coerceIn(5, 100) / 2).dp),
      )
      Box(modifier = Modifier.fillMaxSize().background(baseColor.copy(alpha = 0.55f)))
    }
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .then(
            when (MusicSettings.nowPlayingBg) {
              1 -> Modifier.background(animAccent.copy(alpha = 0.75f).compositeOver(baseColor))
              2, 4 -> Modifier.background(Brush.verticalGradient(listOf(animAccent.copy(alpha = 0.6f), baseColor)))
              5 -> Modifier.background(Brush.radialGradient(listOf(animAccent.copy(alpha = 0.55f), baseColor), radius = 1100f))
              6 -> Modifier.background(lerp(baseColor, MaterialTheme.colorScheme.primary, 0.25f))
              else -> Modifier
            },
          )
          .pointerInput(Unit) {
            // swipe down to close Now playing
            var dy = 0f
            detectVerticalDragGestures(
              onDragStart = { dy = 0f },
              onDragEnd = { if (dy > 250f) onClose() },
            ) { _, d -> dy += d }
          }
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f, fill = true)
            .padding(top = 16.dp)
            .pointerInput(t.id) {
              var dx = 0f
              detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragCancel = { swipeScope.launch { dragX.animateTo(0f, tween(200)) } },
                onDragEnd = {
                  if (MusicSettings.swipeCover && !showLyrics) {
                    if (dx > 150f) {
                      swipeDir.intValue = -1
                      MusicController.previous()
                    } else if (dx < -150f) {
                      swipeDir.intValue = 1
                      MusicController.next()
                    }
                  }
                  swipeScope.launch { dragX.animateTo(0f, tween(200)) }
                },
              ) { _, delta ->
                dx += delta
                if (MusicSettings.swipeCover && !showLyrics) swipeScope.launch { dragX.snapTo(dragX.value + delta) }
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        if (showLyrics) {
          LyricsPanel(
            track = t,
            pos = pos,
            onSeek = {
              MusicController.seekTo(it)
              pos = it
            },
            onEdit = { showEditor = true },
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          // the cover follows the finger, and a new song slides in from the side you swiped towards
          AnimatedContent(
            targetState = t,
            transitionSpec = {
              val d = swipeDir.intValue
              (slideInHorizontally(tween(320)) { it * d } + fadeIn(tween(320))) togetherWith
                (slideOutHorizontally(tween(320)) { -it * d } + fadeOut(tween(200)))
            },
            contentKey = { it.id },
            label = "cover",
            modifier =
              Modifier.graphicsLayer {
                translationX = dragX.value * 0.7f
                alpha = 1f - (kotlin.math.abs(dragX.value) / 900f).coerceAtMost(0.4f)
              },
          ) { tr -> TrackArt(tr, 800, Modifier.fillMaxWidth(coverFraction).aspectRatio(1f), RoundedCornerShape(28.dp)) }
        }
      }
      Spacer(Modifier.height(20.dp))

      Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = MusicFont.medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      Text(t.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(24.dp))

      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { MusicController.cycleRepeat() }) {
          Icon(
            if (MusicController.repeat == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            "Repeat",
            tint = if (MusicController.repeat != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(onClick = { swipeDir.intValue = -1; MusicController.previous() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp)) }
        FilledIconButton(onClick = { MusicController.togglePlay() }, modifier = Modifier.size(88.dp)) {
          Icon(if (MusicController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(44.dp))
        }
        IconButton(onClick = { swipeDir.intValue = 1; MusicController.next() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp)) }
        IconButton(onClick = { MusicController.toggleShuffle() }) {
          Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (MusicController.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
      }
      Spacer(Modifier.height(20.dp))

      Slider(
        value = if (dragging) dragValue else (pos / duration).coerceIn(0f, 1f),
        onValueChange = {
          dragging = true
          dragValue = it
        },
        onValueChangeFinished = {
          MusicController.seekTo((dragValue * duration).toInt())
          pos = (dragValue * duration).toInt()
          dragging = false
        },
      )
      Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(clock(if (dragging) (dragValue * duration).toLong() else pos.toLong()), style = MaterialTheme.typography.bodySmall)
        Text(if (MusicSettings.remainingTime) "-" + clock((t.durationMs - (if (dragging) (dragValue * duration).toLong() else pos.toLong())).coerceAtLeast(0L)) else clock(t.durationMs), style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.height(20.dp))
      if (MusicSettings.songInfo) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp))
      Spacer(Modifier.height(20.dp))

      if (MusicSettings.volumeSlider) {
        val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
        val maxV = remember { am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat() }
        var vol by remember { mutableFloatStateOf(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume")
          Slider(
            value = vol,
            onValueChange = {
              vol = it
              am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            valueRange = 0f..maxV,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
          )
        }
      }
      Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { showLyrics = !showLyrics }) {
          Icon(if (showLyrics) Icons.Filled.SpeakerNotesOff else Icons.Filled.SpeakerNotes, "Lyrics")
        }
        if (MusicSettings.showFavButton) {
          IconButton(onClick = { MusicController.toggleFavorite(t.id) }) {
            Icon(
              if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
              "Favorite",
              tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
        if (MusicSettings.showExtraControls) {
          IconButton(onClick = { showSleep = true }) {
            Icon(Icons.Filled.Bedtime, "Sleep timer", tint = if (MusicController.sleepEndAt > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
          }
          IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") }
        }
        Box {
          IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "More") }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = popupColor()) {
            @Composable
            fun item(
              label: String,
              run: () -> Unit,
            ) = DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; run() })
            item("Sleep timer") { showSleep = true }
            item("Playback speed") { showSpeed = true }
            item("Equalizer") { openEqualizer(context) }
            item("Tag editor") { showTags = true }
            item("Edit lyrics") { showEditor = true }
            item("Add to playlist") { actions.addToPlaylist(t) }
            item("Go to album") { actions.goAlbum(t) }
            item("Go to artist") { actions.goArtist(t) }
            item("Share") { actions.share(t) }
            item("Details") { actions.info(t) }
            item("Show queue") { showQueue = true }
            item("Clear playing queue") { MusicController.clearQueue() }
            item("Stop & close player") { MusicController.stopAll(); onClose() }
          }
        }
      }
    }
    }
  }
  }

  if (showTags) TagEditorDialog(t) { showTags = false }

  if (showEditor) {
    Dialog(onDismissRequest = { showEditor = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      LyricsEditor(track = t, onClose = { showEditor = false })
    }
  }

  if (showQueue) {
    ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = popupColor()) {
      Text("Playing queue  (${MusicController.queue.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
      LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
        itemsIndexed(MusicController.queue.toList(), key = { _, q -> q.id }) { i, q ->
          val cur = i == MusicController.index
          Row(
            modifier = Modifier.fillMaxWidth().clickable { MusicController.playAt(i) }.padding(start = 24.dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            TrackArt(q, 120, Modifier.size(42.dp), RoundedCornerShape(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(q.title, color = if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (cur) FontWeight.Bold else MusicFont.normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(q.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { MusicController.removeFromQueue(i) }) { Icon(Icons.Filled.Close, "Remove") }
          }
        }
      }
    }
  }
  if (showSleep) {
    AlertDialog(
      containerColor = popupColor(),
      onDismissRequest = { showSleep = false },
      title = { Text("Sleep timer") },
      text = {
        Column {
          listOf(5, 10, 15, 30, 45, 60).forEach { m ->
            TextButton(onClick = { MusicController.setSleepTimer(m); showSleep = false }) { Text("$m minutes") }
          }
          if (MusicController.sleepEndAt > 0) {
            TextButton(onClick = { MusicController.setSleepTimer(0); showSleep = false }) { Text("Cancel timer") }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showSleep = false }) { Text("Close") } },
    )
  }
  if (showSpeed) {
    AlertDialog(
      containerColor = popupColor(),
      onDismissRequest = { showSpeed = false },
      title = { Text("Playback speed") },
      text = {
        Column {
          listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
            TextButton(onClick = { MusicController.changeSpeed(s); showSpeed = false }) {
              Text("${s}x" + if (s == MusicController.speed) "  ✓" else "")
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("Close") } },
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TrackArt(
  track: MusicTrack?,
  size: Int,
  modifier: Modifier,
  shape: Shape,
) {
  val context = LocalContext.current
  val finalShape: Shape =
    if (shape is RoundedCornerShape) {
      when (MusicSettings.coverShape) {
        1 -> CircleShape
        2 -> RectangleShape
        else -> shape
      }
    } else {
      shape
    }
  val bmp by produceState<Bitmap?>(null, track?.id, size, MusicController.libraryVersion) {
    value = if (track == null) null else withContext(Dispatchers.IO) { loadArt(context, track, size) }
  }
  Box(
    modifier = modifier.clip(finalShape).background(MaterialTheme.colorScheme.secondaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    val b = bmp
    if (b != null) {
      Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
  }
}

private fun buildAlbums(tracks: List<MusicTrack>): List<AlbumItem> =
  tracks
    .groupBy { if (it.albumId > 0) it.albumId else -it.id }
    .map { (k, v) -> AlbumItem(k, v.first().album, v.first().artist, v.sortedBy { it.title.lowercase() }) }
    .sortedBy { it.name.lowercase() }

private fun splitArtists(a: String): List<String> {
  if (!MusicSettings.multiArtists) return listOf(a)
  val parts = a.split(*MusicSettings.artistSeparators.toCharArray()).map { it.trim() }.filter { it.isNotEmpty() }
  return parts.ifEmpty { listOf(a) }
}

private fun buildArtists(tracks: List<MusicTrack>): List<ArtistItem> =
  tracks
    .flatMap { t -> splitArtists(t.artist).map { it to t } }
    .groupBy({ it.first }, { it.second })
    .map { (name, v) -> ArtistItem(name, v, v.map { it.albumId }.distinct().size) }
    .sortedBy { it.name.lowercase() }

private fun lastAdded(tracks: List<MusicTrack>): List<MusicTrack> {
  val d = MusicSettings.lastAddedDays
  val cutoff = if (d > 0) System.currentTimeMillis() / 1000 - d * 86400L else 0L
  return tracks.filter { it.dateAdded >= cutoff }.sortedByDescending { it.dateAdded }
}

@Composable
private fun FoldersTab(
  folders: List<FolderItem>,
  onOpen: (FolderItem) -> Unit,
  onPlay: (FolderItem) -> Unit,
  onPlayNext: (FolderItem) -> Unit,
  onQueue: (FolderItem) -> Unit,
  onAddToPlaylist: (FolderItem) -> Unit,
) {
  val ctx = LocalContext.current
  var menuFor by remember { mutableStateOf<FolderItem?>(null) }
  if (folders.isEmpty()) {
    Text("No folders found", color = MaterialTheme.colorScheme.outline)
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(folders, key = { it.path }) { f ->
      Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(f) }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
          contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Column(modifier = Modifier.weight(1f)) {
          Text(f.name, fontWeight = MusicFont.medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("${f.tracks.size} songs  •  ${f.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { menuFor = f }) { Icon(Icons.Filled.MoreVert, "More") }
      }
    }
  }
  menuFor?.let { f ->
    ActionSheet(
      title = f.name,
      subtitle = "${f.tracks.size} songs",
      leading = { Icon(Icons.Filled.Folder, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
      items =
        listOf(
          SheetItem("Play", Icons.Filled.PlayArrow) { onPlay(f) },
          SheetItem("Play next", Icons.Filled.SkipNext) { onPlayNext(f) },
          SheetItem("Add to playing queue", Icons.Filled.AddToQueue) { onQueue(f) },
          SheetItem("Add to playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { onAddToPlaylist(f) },
          SheetItem("Blacklist", Icons.Filled.VisibilityOff, divider = true) { blacklistFolder(ctx, f.path) },
        ),
      onDismiss = { menuFor = null },
    )
  }
}

internal class SheetItem(
  val label: String,
  val icon: ImageVector,
  val divider: Boolean = false,
  val onClick: () -> Unit,
)

/** Bottom sheet with a header and a list of actions (like the Oto song and folder menus). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionSheet(
  title: String,
  subtitle: String?,
  leading: @Composable () -> Unit,
  items: List<SheetItem>,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = popupColor()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      leading()
      Column {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    HorizontalDivider()
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
      items.forEach { it0 ->
        if (it0.divider) HorizontalDivider(modifier = Modifier.padding(start = 72.dp, top = 4.dp, bottom = 4.dp))
        Row(
          modifier = Modifier.fillMaxWidth().clickable { onDismiss(); it0.onClick() }.padding(horizontal = 24.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
          Icon(it0.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(it0.label, style = MaterialTheme.typography.bodyLarge)
        }
      }
    }
  }
}

private fun blacklistFolder(
  ctx: Context,
  folder: String,
) {
  val cur = MusicSettings.excluded()
  if (folder !in cur) {
    MusicSettings.excludedFolders = (cur + folder).joinToString("|")
    MusicSettings.save()
  }
  Toast.makeText(ctx, "Folder hidden. Undo it in Settings > Library > Blacklist", Toast.LENGTH_LONG).show()
}

private fun shareTrack(
  context: Context,
  t: MusicTrack,
) {
  val i =
    Intent(Intent.ACTION_SEND).apply {
      type = "audio/*"
      putExtra(Intent.EXTRA_STREAM, t.uri)
      clipData = ClipData.newRawUri("", t.uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  context.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun openEqualizer(context: Context) {
  try {
    val i =
      Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, MusicController.audioSessionId)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    context.startActivity(i)
  } catch (_: Exception) {
    Toast.makeText(context, "No equalizer found on this device", Toast.LENGTH_SHORT).show()
  }
}

private fun formatTrackTime(durationMs: Long): String {
  val totalSeconds = (durationMs / 1000).toInt().coerceAtLeast(0)
  val hours = totalSeconds / 3600
  val minutes = totalSeconds % 3600 / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
