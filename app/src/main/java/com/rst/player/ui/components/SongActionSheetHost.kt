package com.rst.player.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rst.player.AppGraph
import com.rst.player.data.db.entity.PlaylistEntity
import com.rst.player.data.model.Song
import com.rst.player.data.repository.DeleteResult
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun rememberPlaylists(graph: AppGraph): List<PlaylistEntity> {
    val playlists by graph.playlistRepository.observePlaylists()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    return playlists
}

@Composable
fun rememberFavoriteIds(graph: AppGraph): Set<String> {
    val ids by graph.playlistRepository.observeFavoritesSongs()
        .map { list -> list.map { it.songId }.toSet() }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    return ids
}

@Composable
fun SongActionSheetHost(
    graph: AppGraph,
    song: Song?,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onGoToAlbum: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val allSongs by graph.musicRepository.songs.collectAsStateWithLifecycle(initialValue = emptyList())

    var showDeleteSheet by remember { mutableStateOf(false) }
    var songsToDelete by remember { mutableStateOf<List<Song>>(emptyList()) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = songsToDelete
        songsToDelete = emptyList()
        showDeleteSheet = false
        if (result.resultCode == Activity.RESULT_OK && pending.isNotEmpty()) {
            pending.forEach { graph.musicRepository.onSongDeleted(it.id) }
            scope.launch { graph.musicRepository.scan() }
        }
    }

    if (song == null) return
    val favIds by graph.playlistRepository.observeFavoritesSongs()
        .map { list -> list.map { it.songId }.toSet() }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    var showPlaylistDialog by remember { mutableStateOf(false) }

    SongOptionsSheet(
        song = song,
        isFavorite = song.id.toString() in favIds,
        onDismiss = onDismiss,
        onToggleFavorite = {
            scope.launch { graph.playlistRepository.toggleFavorite(song) }
        },
        onAddToPlaylist = { showPlaylistDialog = true },
        onPlayNext = { onPlayNext(song) },
        onGoToAlbum = { onGoToAlbum(song.albumId) },
        onDelete = { showDeleteSheet = true }
    )

    if (showDeleteSheet) {
        DeleteSongsSheet(
            songs = allSongs,
            initialSong = song,
            onDismiss = { showDeleteSheet = false },
            onConfirm = { toDelete ->
                showDeleteSheet = false
                songsToDelete = toDelete
                if (toDelete.any { it.id.toString() == graph.playerController.currentSongId() }) {
                    graph.playerController.stop()
                }
                scope.launch {
                    when (val result = graph.musicRepository.deleteSongs(toDelete)) {
                        is DeleteResult.Deleted -> {
                            onDismiss()
                            graph.musicRepository.scan()
                        }
                        is DeleteResult.NeedsUserApproval -> {
                            onDismiss()
                            deleteLauncher.launch(
                                IntentSenderRequest.Builder(result.intentSender).build()
                            )
                        }
                        else -> onDismiss()
                    }
                }
            }
        )
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            onCreatePlaylist = { name ->
                scope.launch { graph.playlistRepository.createPlaylist(name) }
            },
            onAddToPlaylist = { id ->
                scope.launch {
                    graph.playlistRepository.addSong(id, song)
                    showPlaylistDialog = false
                }
            },
            onDismiss = { showPlaylistDialog = false }
        )
    }
}
