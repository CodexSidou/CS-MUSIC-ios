package com.rst.player.ui.screens.search

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.rst.player.ui.viewmodel.OnlineArtistViewModel
import com.rst.player.youtube.ActiveDownload
import com.rst.player.youtube.YouTubeRepository.OnlineArtistInfo
import com.rst.player.youtube.YouTubeVideo

@Composable
fun OnlineArtistScreen(
    artistId: Long,
    artistName: String,
    onBack: () -> Unit,
    onOpenOnlineAlbum: (Long, String, String) -> Unit
) {
    val graph = LocalGraph.current
    val vm: OnlineArtistViewModel = viewModel(key = "online-artist-$artistId-$artistName") {
        OnlineArtistViewModel(artistId, artistName, graph.youTubeRepository, graph.playerController)
    }
    val profile by vm.profile.collectAsStateWithLifecycle()
    val artistProfile = profile
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val previewingId by vm.previewingId.collectAsStateWithLifecycle()
    val activeDownload by graph.youTubeRepository.downloadTracker.active.collectAsStateWithLifecycle()
    val previewProgressState by graph.youTubeRepository.previewProgress.collectAsStateWithLifecycle()
    val previewProgress = previewProgressState?.progress

    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    val installSelected = {
        val songs = artistProfile?.topSongs?.filter { it.id in selected }.orEmpty()
        if (songs.isNotEmpty()) {
            vm.downloadAll(songs)
            selected = emptySet()
            selecting = false
        }
    }

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
                text = artistName.ifBlank { artistProfile?.name ?: "Artist" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            if (artistProfile != null && artistProfile.topSongs.isNotEmpty()) {
                if (selecting) {
                    TextButton(onClick = {
                        if (selected.size == artistProfile.topSongs.size) selected = emptySet()
                        else selected = artistProfile.topSongs.map { it.id }.toSet()
                    }) {
                        Text(if (selected.size == artistProfile.topSongs.size) "Clear" else "All")
                    }
                } else {
                    TextButton(onClick = { selecting = true }) {
                        Text("Select")
                    }
                }
            }
        }

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                }
            }
            profile == null -> {
                EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = "Couldn't load artist",
                    subtitle = error ?: "Check your connection and try again.",
                    actionLabel = "Back",
                    onAction = onBack
                )
            }
            else -> {
                ArtistProfileContent(
                    profile = artistProfile!!,
                    previewingId = previewingId,
                    previewProgress = previewProgress,
                    activeDownload = activeDownload,
                    selecting = selecting,
                    selectedIds = selected,
                    onToggleSelect = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                    onPlayAll = { vm.playAll(false) },
                    onShuffle = { vm.playAll(true) },
                    onPreview = { vm.playPreview(it) },
                    onDownload = { vm.download(it) },
                    onOpenOnlineAlbum = onOpenOnlineAlbum
                )
            }
        }

        if (artistProfile != null && artistProfile.topSongs.isNotEmpty() && !loading) {
            Surface(
                color = if (selecting) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (selecting) null
                else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selecting) {
                        IconButton(onClick = {
                            selecting = false
                            selected = emptySet()
                        }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Cancel selection",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = if (selected.isEmpty()) "Tap songs to select them"
                            else "Install ${selected.size} song${if (selected.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(onClick = installSelected, enabled = selected.isNotEmpty()) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install")
                        }
                    } else {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                text = "Download songs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${artistProfile.topSongs.size} found · select the ones you want",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { selecting = true }) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistProfileContent(
    profile: OnlineArtistInfo,
    previewingId: String?,
    previewProgress: Float?,
    activeDownload: ActiveDownload?,
    selecting: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPreview: (YouTubeVideo) -> Unit,
    onDownload: (YouTubeVideo) -> Unit,
    onOpenOnlineAlbum: (Long, String, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RemoteArt(profile.thumbnail, Modifier.size(140.dp), cornerRadius = 28.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        profile.genre.takeIf { it.isNotBlank() },
                        "${profile.trackCount} songs",
                        "${profile.albumCount} albums"
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
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
            }
        }

        if (profile.topSongs.isNotEmpty()) {
            item {
                SectionHeader("Top songs", profile.topSongs.size)
            }
            items(profile.topSongs, key = { "ts-${it.id}" }) { video ->
                ArtistTrackRow(
                    video = video,
                    previewing = previewingId == video.id,
                    previewProgress = previewProgress,
                    downloading = activeDownload?.videoId == video.id,
                    downloadProgress = activeDownload?.progress ?: 0f,
                    downloadBusy = activeDownload != null,
                    selecting = selecting,
                    selected = video.id in selectedIds,
                    onToggleSelect = { onToggleSelect(video.id) },
                    onPreview = { onPreview(video) },
                    onDownload = { onDownload(video) }
                )
            }
        }

        if (profile.albums.isNotEmpty()) {
            item {
                SectionHeader("Albums", profile.albumCount)
            }
            items(profile.albums.chunked(2), key = { "alrow-${it.first().id}" }) { rowAlbums ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowAlbums.forEach { album ->
                        AlbumCard(
                            album = album,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenOnlineAlbum(collectionIdOf(album), album.title, album.uploader) }
                        )
                    }
                    if (rowAlbums.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistTrackRow(
    video: YouTubeVideo,
    previewing: Boolean,
    previewProgress: Float?,
    downloading: Boolean,
    downloadProgress: Float,
    downloadBusy: Boolean,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onPreview: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !downloadBusy && !previewing) {
                if (selecting) onToggleSelect() else onPreview()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors()
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
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
                text = formatDurationSeconds(video.duration),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (selecting) {
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            ) {
                IconButton(onClick = onToggleSelect) {
                    Icon(
                        imageVector = if (selected) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = if (selected) "Selected" else "Select",
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (downloading) {
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

@Composable
private fun AlbumCard(album: YouTubeVideo, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            text = if (album.duration > 0) "${album.duration} tracks" else album.uploader,
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
private fun SectionHeader(title: String, count: Int) {    Row(
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

private fun formatDurationSeconds(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val minutes = seconds / 60
    val sec = seconds % 60
    val hours = minutes / 60
    if (hours > 0) {
        return "$hours:${(minutes % 60).toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }
    return "$minutes:${sec.toString().padStart(2, '0')}"
}
