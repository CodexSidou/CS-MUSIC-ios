@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.rst.player.ui.screens.explore

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rst.player.ui.components.EmptyState
import com.rst.player.ui.components.RemoteArt
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.screens.search.ArtistTrackRow
import com.rst.player.ui.theme.BrandAccentBright
import com.rst.player.ui.viewmodel.ExploreTab
import com.rst.player.ui.viewmodel.ExploreViewModel
import com.rst.player.youtube.ActiveDownload
import com.rst.player.youtube.YouTubeChannel
import com.rst.player.youtube.YouTubeVideo
import kotlinx.coroutines.delay

@Composable
fun ExploreScreen(
    onOpenOnlineArtist: (Long, String) -> Unit,
    onOpenOnlineAlbum: (Long, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val graph = LocalGraph.current
    val vm: ExploreViewModel = viewModel(key = "explore") {
        ExploreViewModel(graph.youTubeRepository, graph.playerController)
    }

    val trending by vm.trending.collectAsStateWithLifecycle()
    val topAlbums by vm.topAlbums.collectAsStateWithLifecycle()
    val chartTab by vm.chartTab.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val regionLabel by vm.regionLabel.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val lastQuery by vm.lastQuery.collectAsStateWithLifecycle()
    val previewingId by vm.previewingId.collectAsStateWithLifecycle()
    val previewProgressState by graph.youTubeRepository.previewProgress.collectAsStateWithLifecycle()
    val previewProgress = previewProgressState?.progress
    val activeDownload by graph.youTubeRepository.downloadTracker.active.collectAsStateWithLifecycle()
    val hasMore by vm.hasMore.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()

    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) keyboard?.hide()
        }
    }

    val hasSearch = lastQuery.isNotBlank()
    val albumMode = !hasSearch && chartTab == ExploreTab.ALBUMS
    val shown = when {
        hasSearch -> results
        albumMode -> topAlbums
        else -> trending
    }

    LifecycleResumeEffect(Unit) {
        vm.refreshAsync()
        onPauseOrDispose { }
    }

    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val agoMs = lastUpdated?.let { tick - it } ?: -1L
    val updatedLabel = when {
        agoMs < 0 -> "updating…"
        agoMs < 60_000 -> "just now"
        agoMs < 3_600_000 -> "${agoMs / 60_000}m ago"
        else -> "${agoMs / 3_600_000}h ago"
    }

    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    val installSelected = {
        val songs = shown.filter { it.id in selected }
        if (songs.isNotEmpty()) {
            vm.downloadAll(songs)
            selected = emptySet()
            selecting = false
        }
    }

    // Leaving the songs list (switching to albums, running a search, clearing)
    // always drops the selection so the page never looks stuck in select mode.
    LaunchedEffect(chartTab, hasSearch) {
        selecting = false
        selected = emptySet()
    }

    // System back clears the active search or selection first; only the base
    // chart state lets Back exit the app.
    BackHandler(enabled = hasSearch || selecting) {
        if (selecting) {
            selecting = false
            selected = emptySet()
        } else {
            vm.clearSearch()
        }
    }

    // Infinite scroll: when the chart gets close to the bottom, load the next
    // page of songs. loadMore() no-ops on the albums tab and during search.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore, shown.size, chartTab, hasSearch) {
        if (shouldLoadMore && !loading && !refreshing) vm.loadMore()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasSearch || selecting) {
                    IconButton(
                        onClick = {
                            if (selecting) {
                                selecting = false
                                selected = emptySet()
                            } else {
                                vm.clearSearch()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EXPLORE",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasSearch) "Search results" else if (albumMode) "Top albums" else "Trending now",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (selecting) {
                    TextButton(
                        onClick = {
                            if (selected.size == shown.size) selected = emptySet()
                            else selected = shown.map { it.id }.toSet()
                        },
                        enabled = shown.isNotEmpty()
                    ) {
                        Text(if (selected.size == shown.size && shown.isNotEmpty()) "Clear" else "All")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!albumMode && shown.isNotEmpty()) {
                            TextButton(onClick = {
                                keyboard?.hide()
                                selecting = true
                            }) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select")
                            }
                        }
                        var spin by remember { mutableFloatStateOf(0f) }
                        LaunchedEffect(refreshing) {
                            while (refreshing) {
                                spin += 60f
                                delay(80)
                            }
                        }
                        IconButton(onClick = { vm.refreshAsync() }, enabled = !loading && !refreshing) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = if (refreshing) "Refreshing" else "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(spin)
                            )
                        }
                    }
                }
            }
            if (!hasSearch) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(text = if (albumMode) "${topAlbums.size} albums" else "${trending.size} songs")
                    InfoChip(text = regionLabel)
                    InfoChip(text = "Updated $updatedLabel")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            SearchField(
                query = query,
                onQueryChange = vm::setQuery,
                onSearch = {
                    keyboard?.hide()
                    vm.search()
                },
                onClear = vm::clearSearch
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (!hasSearch) {
            ChartTabs(
                selected = chartTab,
                onSelect = vm::setChartTab,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        // Download progress is shown inside each row's own download button;
        // there is no full-width progress card here to keep the page clean.

        error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
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
                    IconButton(onClick = { vm.refreshAsync() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                loading && shown.isEmpty() -> {
                    ShimmerLoading(albumMode = albumMode)
                }
            searching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Searching online…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            shown.isEmpty() && query.isNotBlank() && !hasSearch -> {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Press search to find results",
                    subtitle = "Hit the search key on your keyboard to look up “${query.trim()}”",
                    actionLabel = "Search",
                    onAction = {
                        keyboard?.hide()
                        vm.search()
                    }
                )
            }
            shown.isEmpty() -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    title = if (albumMode) "No albums right now" else "Nothing trending right now",
                    subtitle = error ?: "Check your connection and refresh.",
                    actionLabel = "Refresh",
                    onAction = { vm.refreshAsync() }
                )
            }
            else -> {
                ExploreContent(
                    videos = if (hasSearch) results else trending,
                    chartAlbums = topAlbums,
                    artists = if (hasSearch) artists else emptyList(),
                    albums = if (hasSearch) albums else emptyList(),
                    hasSearch = hasSearch,
                    albumMode = albumMode,
                    previewingId = previewingId,
                    previewProgress = previewProgress,
                    activeDownload = activeDownload,
                    listState = listState,
                    hasMore = hasMore,
                    loadingMore = loadingMore,
                    onLoadMore = { vm.loadMore() },
                    selecting = selecting,
                    selectedIds = selected,
                    onToggleSelect = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                    onPlayAll = { vm.playAll(false) },
                    onShuffle = { vm.playAll(true) },
                    onEnterSelect = {
                        keyboard?.hide()
                        selecting = true
                    },
                    onPreview = { vm.playPreview(it) },
                    onDownload = { vm.download(it) },
                    onOpenOnlineArtist = onOpenOnlineArtist,
                    onOpenOnlineAlbum = onOpenOnlineAlbum
                )
            }
            }
        }

        if (!albumMode && shown.isNotEmpty() && !loading && !searching) {
            Surface(
                color = if (selecting) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (selecting) null
                else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(4f)
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
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
                            Text("Start")
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
                                text = "${shown.size} found · select the ones you want",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = {
                            keyboard?.hide()
                            selecting = true
                        }) {
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
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Search millions of songs…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    IconButton(onClick = onClear) {
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
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ExploreContent(
    videos: List<YouTubeVideo>,
    chartAlbums: List<YouTubeVideo>,
    artists: List<YouTubeChannel>,
    albums: List<YouTubeVideo>,
    hasSearch: Boolean,
    albumMode: Boolean,
    previewingId: String?,
    previewProgress: Float?,
    activeDownload: ActiveDownload?,
    listState: LazyListState,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    selecting: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onEnterSelect: () -> Unit,
    onPreview: (YouTubeVideo) -> Unit,
    onDownload: (YouTubeVideo) -> Unit,
    onOpenOnlineArtist: (Long, String) -> Unit,
    onOpenOnlineAlbum: (Long, String, String) -> Unit
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
    ) {
        if (!selecting && !albumMode && videos.isNotEmpty()) {
            item {
                HeroCard(
                    video = videos.first(),
                    rank = 1,
                    onPlay = { onPreview(videos.first()) },
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                    onEnterSelect = onEnterSelect
                )
            }
        }

        if (!selecting && albumMode) {
            item {
                SectionTitle("Top albums this week")
            }
            items(chartAlbums.chunked(2), key = { "chartAlbumRow-${it.first().id}" }) { rowAlbums ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowAlbums.forEach { album ->
                        AlbumCard(
                            album = album,
                            onClick = {
                                onOpenOnlineAlbum(collectionIdOf(album), album.title, album.uploader)
                            },
                            modifier = Modifier.weight(1f).animateItemPlacement()
                        )
                    }
                    if (rowAlbums.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (hasSearch && artists.isNotEmpty()) {
            item {
                SectionTitle("Artists")
            }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    items(artists, key = { it.channelUrl.ifBlank { it.name } }) { artist ->
                        ArtistCard(
                            name = artist.name,
                            thumbnail = artist.thumbnail,
                            verified = artist.isVerified,
                            onClick = {
                                val id = artist.artistId
                                onOpenOnlineArtist(id ?: -1L, artist.name)
                            }
                        )
                    }
                }
            }
        }

        if (hasSearch && albums.isNotEmpty()) {
            item {
                SectionTitle("Albums")
            }
            items(albums.chunked(2), key = { "albumRow-${it.first().id}" }) { rowAlbums ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowAlbums.forEach { album ->
                        AlbumCard(
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

        if (!albumMode) {
            item {
                SectionTitle(if (hasSearch) "Songs" else "Trending chart · ${videos.size}")
            }
            itemsIndexed(videos) { index, video ->
                if (selecting || index > 0) {
                    ChartRow(
                        video = video,
                        rank = index + 1,
                        previewing = previewingId == video.id,
                        previewProgress = previewProgress,
                        downloading = activeDownload?.videoId == video.id,
                        downloadProgress = activeDownload?.progress ?: 0f,
                        downloadBusy = activeDownload != null,
                        selecting = selecting,
                        selected = video.id in selectedIds,
                        onToggleSelect = { onToggleSelect(video.id) },
                        onPreview = { onPreview(video) },
                        onDownload = { onDownload(video) },
                        modifier = Modifier.animateItemPlacement()
                    )
                }
            }
            item {
                LoadMoreFooter(loading = loadingMore, hasMore = hasMore, onLoadMore = onLoadMore)
            }
        }
    }
}

@Composable
private fun HeroCard(
    video: YouTubeVideo,
    rank: Int,
    onPlay: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onEnterSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            RemoteArt(video.thumbnail, Modifier.fillMaxSize(), cornerRadius = 24.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "#$rank CHART LEADER",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.5.sp,
                    color = BrandAccentBright,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.uploader.ifBlank { "Unknown artist" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val heroMeta = buildList {
                    val d = formatDuration(video.duration)
                    if (d != "—") add(d)
                    if (video.viewCount > 0) add("${compactCount(video.viewCount)} views")
                }
                if (heroMeta.isNotEmpty()) {
                    Text(
                        text = heroMeta.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.4.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = onPlay,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                    Surface(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                    Surface(
                        onClick = onEnterSelect,
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ChartRow(
    video: YouTubeVideo,
    rank: Int,
    previewing: Boolean,
    previewProgress: Float?,
    downloading: Boolean,
    downloadProgress: Float,
    downloadBusy: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !downloadBusy && !previewing) {
                if (selecting) onToggleSelect() else onPreview()
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            androidx.compose.material3.Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = rank.toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.size(52.dp)) {
            RemoteArt(video.thumbnail, Modifier.size(52.dp), cornerRadius = 12.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                if (previewing) {
                    CircularProgressIndicator(
                        progress = { previewProgress ?: 0f },
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
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
        Spacer(modifier = Modifier.width(14.dp))
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
                text = video.uploader.ifBlank { "Unknown artist" },
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
                        imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.MusicNote,
                        contentDescription = if (selected) "Selected" else "Select",
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (video.duration > 0) {
                Text(
                    text = formatDuration(video.duration),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }
            DownloadButton(
                downloading = downloading,
                downloadProgress = downloadProgress,
                downloadBusy = downloadBusy,
                onDownload = onDownload
            )
        }
    }
}

/** Round download button that becomes a live progress ring + percent while the row downloads. */
@Composable
private fun DownloadButton(
    downloading: Boolean,
    downloadProgress: Float,
    downloadBusy: Boolean,
    onDownload: () -> Unit
) {
    if (downloading) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { downloadProgress.coerceIn(0f, 1f) },
                modifier = Modifier.size(42.dp),
                strokeWidth = 3.5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "${(downloadProgress.coerceIn(0f, 1f) * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(46.dp)
        ) {
            IconButton(onClick = onDownload, enabled = !downloadBusy) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "Download",
                    tint = if (downloadBusy) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    name: String,
    thumbnail: String,
    verified: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Column(
            modifier = Modifier.width(150.dp).padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (thumbnail.isNotBlank()) {
                RemoteArt(thumbnail, Modifier.size(100.dp), cornerRadius = 50.dp)
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp)
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
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (verified) "Official artist" else "Artist",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = if (verified) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: YouTubeVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        RemoteArt(album.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f), cornerRadius = 14.dp)
        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun SectionTitle(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(50),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LoadMoreFooter(loading: Boolean, hasMore: Boolean, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            hasMore -> OutlinedButton(onClick = onLoadMore) {
                Text("Load more")
            }
            else -> Text(
                text = "You're all caught up",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun compactCount(value: Long): String {
    return when {
        value >= 1_000_000_000 -> String.format("%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

@Composable
private fun ChartTabs(
    selected: ExploreTab,
    onSelect: (ExploreTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExploreTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Surface(
                onClick = { onSelect(tab) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (tab == ExploreTab.SONGS) Icons.Rounded.MusicNote else Icons.Rounded.Album,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tab == ExploreTab.SONGS) "Top songs" else "Top albums",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerLoading(albumMode: Boolean) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "x"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val glow = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val sweep = 2000f
    val brush = Brush.linearGradient(
        colors = listOf(base, glow, base),
        start = Offset(-sweep + x * sweep * 2f, 0f),
        end = Offset(x * sweep * 2f, 0f)
    )
    if (albumMode) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item {
                ShimmerBlock(
                    brush,
                    Modifier.padding(horizontal = 20.dp).width(200.dp).height(16.dp),
                    corner = 8.dp
                )
            }
            items(4) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShimmerBlock(brush, Modifier.weight(1f).aspectRatio(1f), corner = 14.dp)
                    ShimmerBlock(brush, Modifier.weight(1f).aspectRatio(1f), corner = 14.dp)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            item {
                ShimmerBlock(
                    brush,
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(1.2f),
                    corner = 24.dp
                )
            }
            item {
                ShimmerBlock(
                    brush,
                    Modifier.padding(start = 20.dp, top = 22.dp).width(140.dp).height(14.dp),
                    corner = 7.dp
                )
            }
            items(6) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(brush, Modifier.width(28.dp).height(20.dp), corner = 6.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    ShimmerBlock(brush, Modifier.size(52.dp), corner = 12.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBlock(brush, Modifier.fillMaxWidth(0.7f).height(14.dp), corner = 7.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBlock(brush, Modifier.fillMaxWidth(0.45f).height(12.dp), corner = 6.dp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ShimmerBlock(brush, Modifier.size(36.dp), corner = 18.dp)
                }
            }
        }
    }
}

@Composable
private fun ShimmerBlock(brush: Brush, modifier: Modifier, corner: Dp = 12.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(brush))
}

private fun collectionIdOf(album: YouTubeVideo): Long =
    album.id.removePrefix("itunes-album-").toLongOrNull() ?: 0L

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
