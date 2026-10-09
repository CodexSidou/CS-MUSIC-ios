package com.rst.player.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rst.player.data.model.Album
import com.rst.player.data.model.Artist
import com.rst.player.data.model.Song
import com.rst.player.ui.components.AlbumArt
import com.rst.player.ui.components.EmptyState
import com.rst.player.ui.components.RemoteArt
import com.rst.player.ui.components.SongActionSheetHost
import com.rst.player.ui.components.SongRow
import com.rst.player.ui.components.rememberPlaylists
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.viewmodel.SearchViewModel
import com.rst.player.youtube.ActiveDownload
import com.rst.player.youtube.YouTubeChannel
import com.rst.player.youtube.YouTubeVideo

@Composable
fun SearchScreen(
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenOnlineArtist: (Long, String) -> Unit,
    onOpenOnlineAlbum: (Long, String, String) -> Unit,
    onOpenDownloads: () -> Unit
) {
    val graph = LocalGraph.current
    val vm: SearchViewModel = viewModel {
        SearchViewModel(graph.musicRepository, graph.youTubeRepository, graph.playerController)
    }
    val playlists = rememberPlaylists(graph)

    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var mode by remember { mutableIntStateOf(0) }

    val query by vm.query.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val onlineResults by vm.onlineResults.collectAsStateWithLifecycle()
    val onlineAlbums by vm.onlineAlbums.collectAsStateWithLifecycle()
    val onlineArtists by vm.onlineArtists.collectAsStateWithLifecycle()
    val onlineSearching by vm.onlineSearching.collectAsStateWithLifecycle()
    val onlineError by vm.onlineError.collectAsStateWithLifecycle()
    val lastOnlineQuery by vm.lastOnlineQuery.collectAsStateWithLifecycle()
    val previewingId by vm.previewingId.collectAsStateWithLifecycle()
    val previewProgressState by graph.youTubeRepository.previewProgress.collectAsStateWithLifecycle()
    val previewProgress = previewProgressState?.progress
    val playerUi by graph.playerController.ui.collectAsStateWithLifecycle()
    val activeDownload by graph.youTubeRepository.downloadTracker.active.collectAsStateWithLifecycle()

    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) keyboard?.hide()
        }
    }
    // Once an online search finishes, the keyboard is no longer needed — leaving
    // it open covers the bottom nav with the IME and makes tabs feel dead.
    LaunchedEffect(onlineSearching, onlineResults, onlineAlbums, onlineArtists) {
        if (!onlineSearching && lastOnlineQuery.isNotBlank()) keyboard?.hide()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                TextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = {
                        Text(if (mode == 1) "Search millions of songs online…" else "Search songs, artists, albums…")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            if (mode == 1) vm.searchOnline()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
            IconButton(onClick = onOpenDownloads) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "Downloads",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeToggle(
                label = "Library",
                selected = mode == 0,
                onClick = { mode = 0 }
            )
            ModeToggle(
                label = "Online",
                selected = mode == 1,
                onClick = { mode = 1 }
            )
        }

        if (mode == 0) {
            LibraryContent(
                query = query,
                songs = songs,
                albums = albums,
                artists = artists,
                playerUi = playerUi,
                listState = listState,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenDownloads = onOpenDownloads,
                onPlayQueue = { index ->
                    graph.playerController.playQueue(songs, index, false)
                },
                onSongSelected = { selectedSong = it }
            )
        } else {
            OnlineContent(
                query = query,
                results = onlineResults,
                albums = onlineAlbums,
                artists = onlineArtists,
                searching = onlineSearching,
                error = onlineError,
                lastQuery = lastOnlineQuery,
                activeDownload = activeDownload,
                previewingId = previewingId,
                previewProgress = previewProgress,
                listState = listState,
                onSearch = {
                    keyboard?.hide()
                    vm.searchOnline()
                },
                onSearchArtist = vm::searchArtist,
                onOpenOnlineArtist = onOpenOnlineArtist,
                onOpenOnlineAlbum = onOpenOnlineAlbum,
                onClearError = vm::clearOnlineError,
                onPreview = { video -> vm.playPreview(video) },
                onOpenRowArtist = { name -> onOpenOnlineArtist(-1, name) },
                onDownload = { video -> graph.youTubeRepository.runDownload(video) }
            )
        }
    }

    SongActionSheetHost(
        graph = graph,
        song = selectedSong,
        playlists = playlists,
        onDismiss = { selectedSong = null },
        onPlayNext = { song ->
            graph.playerController.playQueue(listOf(song), 0, false)
            selectedSong = null
        },
        onGoToAlbum = { id -> selectedSong = null; onOpenAlbum(id) }
    )
}

@Composable
private fun ModeToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LibraryContent(
    query: String,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playerUi: com.rst.player.player.PlaybackUiState,
    listState: LazyListState,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onPlayQueue: (Int) -> Unit,
    onSongSelected: (Song) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (songs.isNotEmpty()) {
            item {
                SectionHeader("Songs", songs.size)
            }
            items(songs, key = { "song-${it.id}" }) { song ->
                SongRow(
                    song = song,
                    isCurrent = playerUi.currentMediaId == song.id.toString(),
                    isPlaying = playerUi.isPlaying && playerUi.currentMediaId == song.id.toString(),
                    onClick = { onPlayQueue(songs.indexOfFirst { s -> s.id == song.id }) },
                    onLongClick = { onSongSelected(song) }
                )
            }
        }
        if (albums.isNotEmpty()) {
            item {
                SectionHeader("Albums", albums.size)
            }
            items(albums.chunked(2), key = { "albumRow-${it.first().id}" }) { rowAlbums ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowAlbums.forEach { album ->
                        AlbumGridItem(
                            album = album,
                            onClick = { onOpenAlbum(album.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowAlbums.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (artists.isNotEmpty()) {
            item {
                SectionHeader("Artists", artists.size)
            }
            items(artists, key = { "artist-${it.name}" }) { artist ->
                ArtistRow(artist = artist, onClick = { onOpenArtist(artist.name) })
            }
        }
        if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = if (query.isEmpty()) "Search your library" else "No results",
                    subtitle = if (query.isEmpty()) "Find songs, albums and artists instantly" else "Try a different search term",
                    actionLabel = if (query.isEmpty()) "Go to Downloads" else null,
                    onAction = if (query.isEmpty()) ({ onOpenDownloads() }) else null
                )
            }
        }
    }
}

@Composable
private fun OnlineContent(
    query: String,
    results: List<YouTubeVideo>,
    albums: List<YouTubeVideo>,
    artists: List<YouTubeChannel>,
    searching: Boolean,
    error: String?,
    lastQuery: String,
    activeDownload: ActiveDownload?,
    previewingId: String?,
    previewProgress: Float?,
    listState: LazyListState,
    onSearch: () -> Unit,
    onSearchArtist: (String) -> Unit,
    onOpenOnlineArtist: (Long, String) -> Unit,
    onOpenOnlineAlbum: (Long, String, String) -> Unit,
    onClearError: () -> Unit,
    onPreview: (YouTubeVideo) -> Unit,
    onOpenRowArtist: (String) -> Unit,
    onDownload: (YouTubeVideo) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        activeDownload?.let { d ->
            val isDone = d.state.endsWith("Done") || d.state.endsWith("Already in library")
            val isError = d.state.endsWith("Error")
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isError -> Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                isDone -> Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                else -> CircularProgressIndicator(
                                    progress = { d.progress },
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = d.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = d.state,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isError -> MaterialTheme.colorScheme.error
                                    isDone -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (!isDone && !isError) {
                            Text(
                                text = "${(d.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!isDone && !isError) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { d.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }
            }
        }

        error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        when {
            searching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Searching online…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Looking for songs, artists and albums",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            query.trim().isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Search the web for music",
                    subtitle = "Find any song, artist or album and download it straight into your library"
                )
            }
            lastQuery != query.trim() -> {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Press search to find results",
                    subtitle = "Hit the search key on your keyboard to look up “${query.trim()}”",
                    actionLabel = "Search",
                    onAction = onSearch
                )
            }
            artists.isEmpty() && results.isEmpty() && albums.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = "No results",
                    subtitle = "Nothing found for “$query”. Try a different search.",
                    actionLabel = "Try again",
                    onAction = onSearch
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (artists.isNotEmpty()) {
                        item {
                            SectionHeader("Artists", artists.size)
                        }
                        item {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(artists, key = { it.channelUrl.ifBlank { it.name } }) { artist ->
                                    ArtistCard(
                                        name = artist.name,
                                        thumbnail = artist.thumbnail,
                                        onClick = {
                                            val id = artist.artistId
                                            onOpenOnlineArtist(id ?: -1L, artist.name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (albums.isNotEmpty()) {
                        item {
                            SectionHeader("Albums", albums.size)
                        }
                        items(albums.chunked(2), key = { "albumRow-${it.first().id}" }) { rowAlbums ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowAlbums.forEach { album ->
                                    OnlineAlbumCard(
                                        album = album,
                                        onClick = {
                                            onOpenOnlineAlbum(collectionIdOf(album), album.title, album.uploader)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowAlbums.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (results.isNotEmpty()) {
                        item {
                            SectionHeader("Songs", results.size)
                        }
                        items(results, key = { "song-${it.id}" }) { video ->
                            OnlineResultRow(
                                video = video,
                                downloading = activeDownload?.videoId == video.id,
                                downloadProgress = activeDownload?.progress ?: 0f,
                                downloadBusy = activeDownload != null,
                                previewing = previewingId == video.id,
                                previewProgress = previewProgress,
                                onPreview = { onPreview(video) },
                                onOpenArtist = { onOpenRowArtist(video.uploader) },
                                onDownload = { onDownload(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(
    name: String,
    thumbnail: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Column(
            modifier = Modifier.width(140.dp).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (thumbnail.isNotBlank()) {
                RemoteArt(thumbnail, Modifier.size(96.dp), cornerRadius = 48.dp)
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Artist",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OnlineAlbumCard(
    album: YouTubeVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        RemoteArt(album.thumbnail, Modifier.fillMaxWidth().height(150.dp), cornerRadius = 10.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = album.uploader.ifBlank { "Unknown artist" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun collectionIdOf(album: YouTubeVideo): Long =
    album.id.removePrefix("itunes-album-").toLongOrNull() ?: 0L

@Composable
private fun OnlineResultRow(
    video: YouTubeVideo,
    downloading: Boolean,
    downloadProgress: Float,
    downloadBusy: Boolean,
    previewing: Boolean,
    previewProgress: Float?,
    onPreview: () -> Unit,
    onOpenArtist: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !downloadBusy && !previewing) { onPreview() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            RemoteArt(video.thumbnail, Modifier.size(52.dp), cornerRadius = 10.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                if (previewing) {
                    if (previewProgress != null && previewProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { previewProgress },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play preview",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${video.uploader.ifBlank { "Unknown" }} • ${formatDuration(video.duration)}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(
                    enabled = !downloadBusy && !previewing
                ) { onOpenArtist() }
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (downloading) {
            CircularProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.size(26.dp),
                strokeWidth = 3.dp
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ) {
                IconButton(
                    onClick = onDownload,
                    enabled = !downloadBusy
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "Live"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.clickable { onClick() }) {
        AlbumArt(album.id, Modifier.fillMaxWidth().height(150.dp), cornerRadius = 10.dp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = artist.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${artist.songCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
