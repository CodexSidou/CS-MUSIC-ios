package com.rst.player.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rst.player.data.model.Album
import com.rst.player.data.model.Artist
import com.rst.player.data.model.Folder
import com.rst.player.data.model.Song
import com.rst.player.ui.components.AlbumArt
import com.rst.player.ui.components.EmptyState
import com.rst.player.ui.components.GlassScrim
import com.rst.player.ui.components.PillTabs
import com.rst.player.ui.components.SongActionSheetHost
import com.rst.player.ui.components.SongRow
import com.rst.player.ui.components.glassBorder
import com.rst.player.ui.components.neonShadow
import com.rst.player.ui.components.neonShadowSoft
import com.rst.player.ui.components.rememberPlaylists
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import com.rst.player.ui.viewmodel.LibraryViewModel
import com.rst.player.util.formatDuration

@Composable
fun LibraryScreen(
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val graph = LocalGraph.current
    val vm: LibraryViewModel = viewModel {
        LibraryViewModel(graph.musicRepository, graph.playlistRepository)
    }
    val songs by graph.musicRepository.songs.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistCounts by vm.playlistCounts.collectAsStateWithLifecycle()
    val playerUi by graph.playerController.ui.collectAsStateWithLifecycle()
    val playlistsAll = rememberPlaylists(graph)
    val settings by graph.userPreferences.settings.collectAsStateWithLifecycle(initialValue = com.rst.player.data.preferences.AppSettings())

    var tab by remember { mutableIntStateOf(0) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val tabTitles = listOf("Songs", "Albums", "Artists", "Playlists", "Folders")
    val albumCount = graph.musicRepository.albums.value.size
    val artistCount = graph.musicRepository.artists.value.size
    val songCount = songs.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BrandAccent.copy(alpha = 0.08f), Color.Transparent),
                    endY = 0.4f
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RST LIBRARY",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$songCount songs Â· $albumCount albums Â· $artistCount artists",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        PillTabs(
            count = tabTitles.size,
            selectedIndex = tab,
            onSelect = { tab = it }
        ) { index ->
            Text(
                text = tabTitles[index],
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
                color = if (tab == index) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        when (tab) {
            0 -> SongsTab(
                songs = songs,
                currentMediaId = playerUi.currentMediaId,
                isPlaying = playerUi.isPlaying,
                onPlay = { list, index, shuffle ->
                    graph.playerController.playQueue(list, index, shuffle)
                },
                onLongClick = { selectedSong = it },
                onRescan = vm::rescan
            )

            1 -> AlbumsTab(
                albums = graph.musicRepository.albums.value,
                onOpen = onOpenAlbum,
                onPlayAlbum = { album ->
                    graph.playerController.playQueue(songs.filter { it.albumId == album.id }, 0, false)
                }
            )
            2 -> ArtistsTab(
                artists = graph.musicRepository.artists.value,
                grid = settings.artistGridView,
                onOpen = onOpenArtist,
                artUri = { name -> graph.musicRepository.artistArtUri(name) }
            )

            3 -> PlaylistsTab(
                playlists = playlists,
                counts = playlistCounts,
                favoritesId = null,
                onCreate = { showCreateDialog = true },
                onOpen = onOpenPlaylist,
                onOpenFavorites = onOpenFavorites
            )

            4 -> FoldersTab(
                folders = graph.musicRepository.folders.value,
                songs = songs,
                onPlay = { list, index, shuffle ->
                    graph.playerController.playQueue(list, index, shuffle)
                }
            )
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name") }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            vm.createPlaylist(name.trim())
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SongActionSheetHost(
        graph = graph,
        song = selectedSong,
        playlists = playlistsAll,
        onDismiss = { selectedSong = null },
        onPlayNext = { song ->
            graph.playerController.playQueue(listOf(song), 0, false)
            selectedSong = null
        },
        onGoToAlbum = { id -> selectedSong = null; onOpenAlbum(id) }
    )
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onLongClick: (Song) -> Unit,
    onRescan: () -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.MusicNote,
            title = "No songs found",
            subtitle = "Scan your device to find your music",
            actionLabel = "Scan now",
            onAction = onRescan
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songs.size} songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(onClick = { onPlay(songs, 0, true) }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Shuffle")
                }
                TextButton(onClick = { onPlay(songs, 0, false) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play all")
                }
            }
        }
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isCurrent = currentMediaId == song.id.toString(),
                isPlaying = isPlaying && currentMediaId == song.id.toString(),
                onClick = { onPlay(songs, songs.indexOfFirst { s -> s.id == song.id }, false) },
                onLongClick = { onLongClick(song) }
            )
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<Album>,
    onOpen: (Long) -> Unit,
    onPlayAlbum: (Album) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyState(Icons.Rounded.MusicNote, "No albums", "Your albums will appear here")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.id to it.name }) { album ->
            Column(modifier = Modifier.clickable { onOpen(album.id) }) {
                Box {
                    AlbumArt(
                        album.id,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .neonShadowSoft(RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp)),
                        cornerRadius = 20.dp
                    )
                    GlassScrim(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BrandAccent)
                            .glassBorder(CircleShape, alpha = 0.25f)
                            .neonShadowSoft(CircleShape, alpha = 0.4f, elevation = 6.dp)
                            .clickable { onPlayAlbum(album) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play album",
                            tint = Color(0xFF0A0A1F),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${album.artist} Â· ${album.songCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<Artist>,
    grid: Boolean,
    onOpen: (String) -> Unit,
    artUri: (String) -> Uri?
) {
    if (artists.isEmpty()) {
        EmptyState(Icons.Rounded.MusicNote, "No artists", "Your artists will appear here")
        return
    }
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists, key = { it.name }) { artist ->
                Column(modifier = Modifier.clickable { onOpen(artist.name) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(CircleShape)
                    ) {
                        val uri = artUri(artist.name)
                        if (uri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .neonShadowSoft(CircleShape, alpha = 0.3f, elevation = 6.dp),
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHigh),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .neonShadowSoft(CircleShape, alpha = 0.3f, elevation = 6.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                BrandAccent.copy(alpha = 0.3f),
                                                Color(0xFF081C12)
                                            )
                                        )
                                    )
                            )
                            Text(
                                text = artist.name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .glassBorder(CircleShape, alpha = 0.3f, width = 1.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${artist.songCount} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        LazyColumn {
            items(artists, key = { it.name }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(artist.name) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = artist.name.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyLarge,
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
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<com.rst.player.data.db.entity.PlaylistEntity>,
    counts: Map<Long, Int>,
    favoritesId: Long?,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenFavorites: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFavorites() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .neonShadowSoft(RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(BrandAccent, Color(0xFF3730A3)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Favorites", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text("Your liked songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create playlist", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistArtwork(name = playlist.name, count = counts[playlist.id] ?: 0)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        text = if (counts[playlist.id] == 1) "1 song" else "${counts[playlist.id] ?: 0} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FoldersTab(
    folders: List<Folder>,
    songs: List<Song>,
    onPlay: (List<Song>, Int, Boolean) -> Unit
) {
    if (folders.isEmpty()) {
        EmptyState(Icons.Rounded.Folder, "No folders", "Songs are grouped by folder here")
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(folders, key = { it.path }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val list = songs.filter { s -> s.dataPath.startsWith(folder.path) }
                        onPlay(list, 0, false)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .neonShadowSoft(RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(BrandAccent.copy(alpha = 0.18f), Color(0xFF0A0A0A))
                            )
                        )
                        .glassBorder(RoundedCornerShape(16.dp), alpha = 0.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = BrandAccent.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "${folder.songCount} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistArtwork(name: String, count: Int) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(playlistGradient(name)))
            .glassBorder(RoundedCornerShape(16.dp), alpha = 0.3f)
            .neonShadowSoft(RoundedCornerShape(16.dp), alpha = 0.35f, elevation = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.92f)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

private val playlistGradients = listOf(
    listOf(Color(0xFF2ECC71), Color(0xFF3730A3)),
    listOf(Color(0xFF7C5CFF), Color(0xFF35208A)),
    listOf(Color(0xFFFF8A3D), Color(0xFF9C4A12)),
    listOf(Color(0xFF2EC4B6), Color(0xFF0F7A6E)),
    listOf(Color(0xFFFFD166), Color(0xFFB57E00)),
    listOf(Color(0xFFFF5C7A), Color(0xFF9E2A44)),
    listOf(Color(0xFF4DA3FF), Color(0xFF1D5FA8)),
    listOf(Color(0xFFB06BFF), Color(0xFF5B2AA8))
)

private fun playlistGradient(name: String): List<Color> =
    playlistGradients[Math.floorMod(name.hashCode(), playlistGradients.size)]
