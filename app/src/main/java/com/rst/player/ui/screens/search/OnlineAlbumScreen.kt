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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rst.player.ui.components.EmptyState
import com.rst.player.ui.components.RemoteArt
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.viewmodel.OnlineAlbumViewModel
import com.rst.player.youtube.ActiveDownload
import com.rst.player.youtube.YouTubeRepository.OnlineAlbumInfo
import com.rst.player.youtube.YouTubeVideo

@Composable
fun OnlineAlbumScreen(
    collectionId: Long,
    albumName: String,
    artistName: String,
    onBack: () -> Unit
) {
    val graph = LocalGraph.current
    val vm: OnlineAlbumViewModel = viewModel(key = "online-album-$collectionId") {
        OnlineAlbumViewModel(collectionId, graph.youTubeRepository, graph.playerController)
    }
    val album by vm.album.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val previewingId by vm.previewingId.collectAsStateWithLifecycle()
    val activeDownload by graph.youTubeRepository.downloadTracker.active.collectAsStateWithLifecycle()
    val previewProgressState by graph.youTubeRepository.previewProgress.collectAsStateWithLifecycle()
    val previewProgress = previewProgressState?.progress

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
                text = album?.name ?: albumName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                }
            }
            album == null -> {
                EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = "Couldn't load album",
                    subtitle = listOfNotNull(
                        artistName.ifBlank { null },
                        error ?: "Check your connection and try again."
                    ).joinToString(" · "),
                    actionLabel = "Back",
                    onAction = onBack
                )
            }
            else -> {
                AlbumContent(
                    album = album!!,
                    previewingId = previewingId,
                    previewProgress = previewProgress,
                    activeDownload = activeDownload,
                    onPlayAll = { vm.playAll(false) },
                    onShuffle = { vm.playAll(true) },
                    onPreview = { vm.playPreview(it) },
                    onDownload = { vm.download(it) },
                    onDownloadAll = { vm.downloadAll() }
                )
            }
        }
    }
}

@Composable
private fun AlbumContent(
    album: OnlineAlbumInfo,
    previewingId: String?,
    previewProgress: Float?,
    activeDownload: ActiveDownload?,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPreview: (YouTubeVideo) -> Unit,
    onDownload: (YouTubeVideo) -> Unit,
    onDownloadAll: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RemoteArt(album.thumbnail, Modifier.size(160.dp), cornerRadius = 28.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = album.artist.ifBlank { "Unknown artist" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${album.trackCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onPlayAll) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Play")
                    }
                    OutlinedButton(onClick = onShuffle) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onDownloadAll) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text("Install album")
                }
            }
        }

        items(album.tracks, key = { it.id }) { video ->
            ArtistTrackRow(
                video = video,
                previewing = previewingId == video.id,
                previewProgress = previewProgress,
                downloading = activeDownload?.videoId == video.id,
                downloadProgress = activeDownload?.progress ?: 0f,
                downloadBusy = activeDownload != null,
                onPreview = { onPreview(video) },
                onDownload = { onDownload(video) }
            )
        }
    }
}
