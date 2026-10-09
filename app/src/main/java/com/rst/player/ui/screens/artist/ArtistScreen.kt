package com.rst.player.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rst.player.data.model.Song
import com.rst.player.ui.components.EmptyState
import com.rst.player.ui.components.SongActionSheetHost
import com.rst.player.ui.components.SongRow
import com.rst.player.ui.components.rememberPlaylists
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.viewmodel.ArtistViewModel

@Composable
fun ArtistScreen(artistName: String, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val vm: ArtistViewModel = viewModel(key = "artist-$artistName") {
        ArtistViewModel(artistName, graph.musicRepository, graph.playlistRepository)
    }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playerUi by graph.playerController.ui.collectAsStateWithLifecycle()
    val playlists = rememberPlaylists(graph)

    var selectedSong by remember { mutableStateOf<Song?>(null) }

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
                text = artistName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (songs.isEmpty()) {
            EmptyState(Icons.Rounded.QueueMusic, "No songs", "")
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
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${songs.size} songs",
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
