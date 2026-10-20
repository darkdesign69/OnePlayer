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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
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

    LaunchedEffect(granted, reloadKey, MusicSettings.minDurationSec) {
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
    var showNow by remember { mutableStateOf(false) }
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
    var infoTarget by remember { mutableStateOf<MusicTrack?>(null) }
    var newPlaylistFor by remember { mutableStateOf<Long?>(null) }
    var newPlaylistDialog by remember { mutableStateOf(false) }

    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val albums = remember(tracks) { buildAlbums(tracks) }
    val artists = remember(tracks) { buildArtists(tracks) }
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
      if (shuffle) MusicController.shuffleAll(list) else MusicController.play(list, i, false)
    }

    fun openNow() {
      if (MusicSettings.openNowOnPlay) showNow = true
    }

    fun detailTracks(d: Detail): List<MusicTrack> =
      when (d) {
        is Detail.AlbumD -> albums.firstOrNull { it.key == d.key }?.tracks.orEmpty()
        is Detail.ArtistD -> artists.firstOrNull { it.name == d.title }?.tracks.orEmpty()
        is Detail.ListD ->
          when (d.kind) {
            "fav" -> MusicController.favorites.mapNotNull { byId[it] }
            "history" -> MusicController.history.mapNotNull { byId[it] }
            "recent" -> tracks.sortedByDescending { it.dateAdded }.take(100)
            else -> MusicController.playlists[d.title].orEmpty().mapNotNull { byId[it] }
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
          detail = Detail.ArtistD(it.artist)
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
                Text(text = if (showSettings) (musicSettingsTitles.getOrNull(settingsPage) ?: "Settings") else (detail?.title ?: "Music"), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
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
                IconButton(onClick = { settingsPage = -1; showSettings = true }) { Icon(Icons.Filled.Settings, "Music settings") }
              } else if (searching && query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
              }
            },
          )
        },
        bottomBar = {
          Column {
            if (MusicController.current != null) MiniPlayer(onOpen = { showNow = true })
            NavigationBar {
              val items =
                listOf(
                  "For you" to Icons.Filled.Home,
                  "Songs" to Icons.Filled.MusicNote,
                  "Albums" to Icons.Filled.Album,
                  "Artists" to Icons.Filled.Person,
                  "Playlists" to Icons.AutoMirrored.Filled.QueueMusic,
                )
              items.forEachIndexed { i, (label, icon) ->
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
                    Text(a.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                      Text(a.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("${a.albums} albums  •  ${a.tracks.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                  }
                }
              }
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
        NowPlaying(onClose = { showNow = false }, actions = actions)
      }
    }

    // ---- dialogs ----
    addTarget?.let { t ->
      AlertDialog(
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
    infoTarget?.let { t ->
      AlertDialog(
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
  val recent = remember(tracks) { tracks.sortedByDescending { it.dateAdded }.take(20) }
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
      item { SectionTitle("Last added", "See all") { onOpen(Detail.ListD("Last added", "recent")) } }
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
              Text(a.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(t.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        DropdownMenu(expanded = menuFor == name, onDismissRequest = { menuFor = null }) {
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
      Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
  val isCurrent = MusicController.current?.id == track.id
  val color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (MusicSettings.showSongArt) TrackArt(track, 140, Modifier.size(48.dp), RoundedCornerShape(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        if (MusicSettings.showSongDuration) "${track.artist}  •  ${formatTrackTime(track.durationMs)}" else track.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Box {
      IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        @Composable
        fun item(
          label: String,
          run: () -> Unit,
        ) = DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; run() })
        item("Play next") { actions.playNext(track) }
        item("Add to playing queue") { actions.addToQueue(track) }
        item(if (track.id in MusicController.favorites) "Remove from favorites" else "Add to favorites") { MusicController.toggleFavorite(track.id) }
        item("Add to playlist") { actions.addToPlaylist(track) }
        item("Go to album") { actions.goAlbum(track) }
        item("Go to artist") { actions.goArtist(track) }
        item("Share") { actions.share(track) }
        item("Details") { actions.info(track) }
        if (extra != null) item("Remove from playlist") { extra() }
      }
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
  Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
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
          Text(t.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
  val blurBmp by produceState<Bitmap?>(null, t.id, MusicSettings.nowPlayingBg) {
    value = if (MusicSettings.nowPlayingBg == 2) withContext(Dispatchers.IO) { loadArt(context, t, 256) } else null
  }
  val coverFraction =
    when (MusicSettings.coverSize) {
      1 -> 0.82f
      2 -> 0.65f
      else -> 1f
    }

  Surface(modifier = Modifier.fillMaxSize(), color = baseColor) {
    val artBg = blurBmp
    Box(modifier = Modifier.fillMaxSize()) {
    if (MusicSettings.nowPlayingBg == 2 && artBg != null) {
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
            if (MusicSettings.nowPlayingBg == 0) {
              Modifier.background(Brush.verticalGradient(listOf(animAccent.copy(alpha = 0.6f), baseColor)))
            } else {
              Modifier
            },
          )
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close") }
        Text("Now playing", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Box {
          IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            @Composable
            fun item(
              label: String,
              run: () -> Unit,
            ) = DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; run() })
            item("Sleep timer") { showSleep = true }
            item("Playback speed") { showSpeed = true }
            item("Equalizer") { openEqualizer(context) }
            item("Add to playlist") { actions.addToPlaylist(t) }
            item("Go to album") { actions.goAlbum(t) }
            item("Go to artist") { actions.goArtist(t) }
            item("Share") { actions.share(t) }
            item("Details") { actions.info(t) }
            item("Clear playing queue") { MusicController.clearQueue() }
            item("Stop & close player") { MusicController.stopAll(); onClose() }
          }
        }
      }

      Spacer(Modifier.height(8.dp))
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f, fill = true)
            .pointerInput(t.id) {
              var dx = 0f
              detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragEnd = {
                  if (MusicSettings.swipeCover) {
                    if (dx > 150f) MusicController.previous() else if (dx < -150f) MusicController.next()
                  }
                },
              ) { _, delta -> dx += delta }
            },
        contentAlignment = Alignment.Center,
      ) {
        TrackArt(t, 800, Modifier.fillMaxWidth(coverFraction).aspectRatio(1f), RoundedCornerShape(28.dp))
      }
      Spacer(Modifier.height(16.dp))

      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(t.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (MusicSettings.showFavButton) IconButton(onClick = { MusicController.toggleFavorite(t.id) }) {
          Icon(
            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            "Favorite",
            tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

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
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTrackTime(if (dragging) (dragValue * duration).toLong() else pos.toLong()), style = MaterialTheme.typography.bodySmall)
        Text(formatTrackTime(t.durationMs), style = MaterialTheme.typography.bodySmall)
      }

      Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { MusicController.toggleShuffle() }) {
          Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (MusicController.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { MusicController.previous() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp)) }
        FilledIconButton(onClick = { MusicController.togglePlay() }, modifier = Modifier.size(72.dp)) {
          Icon(if (MusicController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(40.dp))
        }
        IconButton(onClick = { MusicController.next() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp)) }
        IconButton(onClick = { MusicController.cycleRepeat() }) {
          Icon(
            if (MusicController.repeat == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            "Repeat",
            tint = if (MusicController.repeat != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      if (MusicSettings.showExtraControls) Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") }
        IconButton(onClick = { showSleep = true }) {
          Icon(Icons.Filled.Timer, "Sleep timer", tint = if (MusicController.sleepEndAt > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { showSpeed = true }) {
          Icon(Icons.Filled.Speed, "Speed", tint = if (MusicController.speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { openEqualizer(context) }) { Icon(Icons.Filled.Equalizer, "Equalizer") }
        IconButton(onClick = { actions.info(t) }) { Icon(Icons.Filled.Info, "Details") }
      }
    }
    }
  }

  if (showQueue) {
    ModalBottomSheet(onDismissRequest = { showQueue = false }) {
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
              Text(q.title, color = if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun TrackArt(
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
  val bmp by produceState<Bitmap?>(null, track?.id, size) {
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

private fun buildArtists(tracks: List<MusicTrack>): List<ArtistItem> =
  tracks
    .groupBy { it.artist }
    .map { (name, v) -> ArtistItem(name, v, v.map { it.albumId }.distinct().size) }
    .sortedBy { it.name.lowercase() }

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
