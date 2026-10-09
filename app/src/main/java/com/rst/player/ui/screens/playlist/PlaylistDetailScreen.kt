package com.rst.player.ui.screens.playlist

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rst.player.ui.components.AlbumArt
import com.rst.player.ui.components.EmptyState
import com.rst.player.ui.components.SongRow
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.viewmodel.PlaylistDetailViewModel

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    isFavorites: Boolean,
    onBack: () -> Unit
) {
    val graph = LocalGraph.current
    val vm: PlaylistDetailViewModel = viewModel(key = "playlist-$playlistId-$isFavorites") {
        PlaylistDetailViewModel(
            playlistId,
            isFavorites,
            graph.playlistRepository,
            graph.musicRepository
        )
    }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val playerUi by graph.playerController.ui.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }

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
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!isFavorites) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (songs.isEmpty()) {
            EmptyState(Icons.Rounded.QueueMusic, "Playlist is empty", "Songs you add will appear here")
            return
        }

        val playbackSongs = vm.resolveForPlayback()

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = { graph.playerController.playQueue(playbackSongs, 0, false) }
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = { graph.playerController.playQueue(playbackSongs, 0, true) }
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Shuffle")
                    }
                }
            }
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = playerUi.currentMediaId == song.id.toString(),
                    isPlaying = playerUi.isPlaying && playerUi.currentMediaId == song.id.toString(),
                    onClick = { graph.playerController.playQueue(playbackSongs, playbackSongs.indexOfFirst { s -> s.id == song.id }, false) },
                    onLongClick = { vm.removeSong(song) }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"$name\" will be deleted permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deletePlaylist()
                        showDeleteDialog = false
                        onBack()
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
