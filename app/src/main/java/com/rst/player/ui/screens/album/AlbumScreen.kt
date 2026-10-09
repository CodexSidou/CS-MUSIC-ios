package com.rst.player.ui.screens.album

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rst.player.data.model.Song
import com.rst.player.ui.components.AlbumArt
import com.rst.player.ui.components.EmptyState
import com.rst.player.ui.components.SongActionSheetHost
import com.rst.player.ui.components.SongRow
import com.rst.player.ui.components.rememberPlaylists
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.viewmodel.AlbumViewModel

@Composable
fun AlbumScreen(albumId: Long, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: AlbumViewModel = viewModel(key = "album-$albumId") {
        AlbumViewModel(albumId, graph.musicRepository, graph.playlistRepository)
    }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playerUi by graph.playerController.ui.collectAsStateWithLifecycle()
    val playlists = rememberPlaylists(graph)

    var selectedSong by remember { mutableStateOf<Song?>(null) }

    val album = songs.firstOrNull()?.album ?: "Album"

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = album,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (songs.isEmpty()) {
            EmptyState(Icons.Rounded.QueueMusic, "Empty album", "")
            return
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AlbumArt(albumId, Modifier.size(220.dp), cornerRadius = 14.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = album,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${songs.firstOrNull()?.artist ?: "Unknown Artist"} • ${songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { graph.playerController.playQueue(songs, 0, false) }
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.padding(start = 6.dp))
                            Text("Play")
                        }
                        OutlinedButton(
                            onClick = { graph.playerController.playQueue(songs, 0, true) }
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.padding(start = 6.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = playerUi.currentMediaId == song.id.toString(),
                    isPlaying = playerUi.isPlaying && playerUi.currentMediaId == song.id.toString(),
                    onClick = { graph.playerController.playQueue(songs, songs.indexOfFirst { s -> s.id == song.id }, false) },
                    onLongClick = { selectedSong = song }
                )
            }
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
        onGoToAlbum = { selectedSong = null }
    )
}
