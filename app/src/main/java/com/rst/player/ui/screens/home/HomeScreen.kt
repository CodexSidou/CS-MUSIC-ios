package com.rst.player.ui.screens.home

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rst.player.data.db.entity.SongMoodEntity
import com.rst.player.data.model.Song
import com.rst.player.recommender.MoodClassifier
import com.rst.player.recommender.MoodTag
import com.rst.player.ui.components.AlbumArt
import com.rst.player.ui.components.EmptyState
import com.rst.player.ui.components.GlassScrim
import com.rst.player.ui.components.GlowRing
import com.rst.player.ui.components.SongActionSheetHost
import com.rst.player.ui.components.SongRow
import com.rst.player.ui.components.glassBorder
import com.rst.player.ui.components.neonShadowSoft
import com.rst.player.ui.components.rememberPlaylists
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.screens.mode.ModeGlyphIcon
import com.rst.player.ui.theme.BrandAccentGlow
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import com.rst.player.ui.viewmodel.HomeViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpenNowPlaying: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenModes: () -> Unit
) {
    val graph = LocalGraph.current
    val vm: HomeViewModel = viewModel {
        HomeViewModel(graph.musicRepository, graph.recommendationEngine, graph.historyRepository, graph.moodRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val playerUi by graph.playerController.ui.collectAsStateWithLifecycle()
    val playlists = rememberPlaylists(graph)

    var selectedSong by remember { mutableStateOf<Song?>(null) }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetingWord = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "night"
    }
    val greetingSubtitle = when (hour) {
        in 5..11 -> "Start the day with a beat"
        in 12..16 -> "Keep the day rolling"
        in 17..21 -> "Unwind with some tunes"
        else -> "Night vibes only"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            val heroTransition = rememberInfiniteTransition(label = "heroGlow")
            val glowAlpha by heroTransition.animateFloat(
                initialValue = 0.16f,
                targetValue = 0.38f,
                animationSpec = infiniteRepeatable(
                    tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "heroGlowAlpha"
            )
            val glowDrift by heroTransition.animateFloat(
                initialValue = (-6f),
                targetValue = 10f,
                animationSpec = infiniteRepeatable(
                    tween(3200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "heroGlowDrift"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(BrandAccentGlow.copy(alpha = glowAlpha * 0.55f), Color.Transparent)
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(240.dp)
                        .offset(x = glowDrift.dp, y = (-90).dp)
                        .background(
                            Brush.radialGradient(
                                listOf(BrandAccentGlow.copy(alpha = glowAlpha), Color.Transparent)
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, top = 10.dp, end = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RST PLAYER",
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildAnnotatedString {
                                append("Good ")
                                withStyle(SpanStyle(color = BrandAccentBright, fontWeight = FontWeight.SemiBold)) {
                                    append(greetingWord)
                                }
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = greetingSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = onOpenModes) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(BrandAccent, Color(0xFF3730A3))
                                    )
                                )
                                .glassBorder(CircleShape, alpha = 0.4f),
                            contentAlignment = Alignment.Center
                        ) {
                            ModeGlyphIcon(
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.songCount == 0) {
            item {
                EmptyState(
                    icon = Icons.Rounded.MusicNote,
                    title = "Your library is empty",
                    subtitle = "Allow music access and scan your device",
                    actionLabel = if (state.scanning) "Scanningâ€¦" else "Scan music",
                    onAction = { if (!state.scanning) vm.rescan() }
                )
            }
            return@LazyColumn
        }

        if (state.topArtists.isNotEmpty()) {
            item { SectionHeader("Your top artists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.topArtists, key = { it.name }) { artist ->
                        ArtistCard(
                            name = artist.name,
                            artUri = artist.artUri,
                            onClick = { onOpenArtist(artist.name) }
                        )
                    }
                }
            }
        }

        item {
            SongCarousel(
                title = "On Repeat",
                songs = state.onRepeat,
                currentMediaId = playerUi.currentMediaId,
                isPlaying = playerUi.isPlaying,
                onPlay = { list, i -> graph.playerController.playQueue(list, i, false) },
                onLongClick = { selectedSong = it }
            )
        }

        item {
            DiscoverCarousel(
                songs = state.discover,
                currentMediaId = playerUi.currentMediaId,
                isPlaying = playerUi.isPlaying,
                onPlay = { list, i -> graph.playerController.playQueue(list, i, false) },
                onLongClick = { selectedSong = it }
            )
        }

        if (state.recentlyPlayed.isNotEmpty()) {
            item { SectionHeader("Recently played") }
            items(state.recentlyPlayed, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = playerUi.currentMediaId == song.id.toString(),
                    isPlaying = playerUi.isPlaying && playerUi.currentMediaId == song.id.toString(),
                    onClick = { graph.playerController.playQueue(state.recentlyPlayed, indexOf(song, state.recentlyPlayed), false) },
                    onLongClick = { selectedSong = song }
                )
            }
        }

        item {
            MoodSongCarousel(
                title = state.madeForYouTitle,
                songs = state.madeForYou,
                moods = state.moods,
                currentMediaId = playerUi.currentMediaId,
                isPlaying = playerUi.isPlaying,
                onPlay = { list, i -> graph.playerController.playQueue(list, i, false) },
                onLongClick = { selectedSong = it }
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

private fun indexOf(song: Song, list: List<Song>): Int = list.indexOfFirst { it.id == song.id }

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SongCarousel(
    title: String,
    songs: List<Song>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onLongClick: (Song) -> Unit
) {
    if (songs.isEmpty()) return
    Column {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongCard(
                    song = song,
                    isCurrent = currentMediaId == song.id.toString(),
                    isPlaying = isPlaying && currentMediaId == song.id.toString(),
                    onClick = { onPlay(songs, indexOf(song, songs)) },
                    onLongClick = { onLongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverCarousel(
    songs: List<Song>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onLongClick: (Song) -> Unit
) {
    if (songs.isEmpty()) return
    Column {
        SectionHeader("Discover something new")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                DiscoverCard(
                    song = song,
                    isCurrent = currentMediaId == song.id.toString(),
                    isPlaying = isPlaying && currentMediaId == song.id.toString(),
                    onClick = { onPlay(songs, indexOf(song, songs)) },
                    onLongClick = { onLongClick(song) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverCard(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .width(232.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF122A1D),
                            Color(0xFF0B141E)
                        )
                    )
                )
                .glassBorder(RoundedCornerShape(20.dp), alpha = 0.22f, width = 1.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                song.albumId,
                Modifier
                    .size(56.dp)
                    .neonShadowSoft(RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)),
                cornerRadius = 14.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NEW",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    color = BrandAccentBright
                )
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) BrandAccent else BrandAccent.copy(alpha = 0.22f))
                    .neonShadowSoft(CircleShape, alpha = 0.35f, elevation = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent) Color(0xFF0A0A1F) else BrandAccentBright,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MoodSongCarousel(
    title: String,
    songs: List<Song>,
    moods: Map<String, SongMoodEntity>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onLongClick: (Song) -> Unit
) {
    if (songs.isEmpty()) return
    Column {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                MoodSongCard(
                    song = song,
                    mood = moods[song.id.toString()],
                    isCurrent = currentMediaId == song.id.toString(),
                    isPlaying = isPlaying && currentMediaId == song.id.toString(),
                    onClick = { onPlay(songs, indexOf(song, songs)) },
                    onLongClick = { onLongClick(song) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoodSongCard(
    song: Song,
    mood: SongMoodEntity?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val tag = mood?.let { MoodClassifier.classify(it) }
    val accent = tag?.let { moodColor(it) }
    Column(
        modifier = Modifier
            .width(150.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.size(150.dp)) {
            AlbumArt(
                song.albumId,
                Modifier
                    .size(150.dp)
                    .neonShadowSoft(RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)),
                cornerRadius = 20.dp
            )
            GlassScrim(alpha = 0.3f)
            if (isCurrent) {
                GlowRing(shape = RoundedCornerShape(20.dp))
            }
            tag?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent ?: BrandAccent)
                        .neonShadowSoft(RoundedCornerShape(50), alpha = 0.35f, elevation = 5.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = it.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFF0A1212)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) BrandAccent else Color.Black.copy(alpha = 0.38f))
                    .glassBorder(CircleShape, alpha = 0.25f)
                    .neonShadowSoft(CircleShape, alpha = 0.4f, elevation = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent) Color(0xFF0A0A1F) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun moodColor(tag: MoodTag): Color = when (tag) {
    MoodTag.NIGHT -> Color(0xFF7C5CFF)
    MoodTag.UPBEAT -> Color(0xFFFF8A3D)
    MoodTag.BRIGHT -> Color(0xFFFFD166)
    MoodTag.CHILL -> Color(0xFF2EC4B6)
    MoodTag.MELLOW -> Color(0xFF8AA0B8)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongCard(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.size(150.dp)) {
            AlbumArt(
                song.albumId,
                Modifier
                    .size(150.dp)
                    .neonShadowSoft(RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)),
                cornerRadius = 20.dp
            )
            GlassScrim(alpha = 0.42f)
            if (isCurrent) {
                GlowRing(shape = RoundedCornerShape(20.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) BrandAccent else Color.Black.copy(alpha = 0.38f))
                    .glassBorder(CircleShape, alpha = 0.25f)
                    .neonShadowSoft(CircleShape, alpha = 0.4f, elevation = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent) Color(0xFF0A0A1F) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistCard(name: String, artUri: Uri?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
        ) {
            if (artUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artUri)
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
                                    BrandAccent.copy(alpha = 0.38f),
                                    Color(0xFF081C12)
                                )
                            )
                        )
                )
                Text(
                    text = name.take(1).uppercase(),
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
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
