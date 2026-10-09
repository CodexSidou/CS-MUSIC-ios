package com.rst.player.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.rst.player.data.mediastore.MediaStoreScanner
import com.rst.player.data.model.Song
import com.rst.player.player.NowPlayingItem
import com.rst.player.ui.components.AlbumArt
import com.rst.player.ui.components.EqualizerBars
import com.rst.player.ui.components.RemoteArt
import com.rst.player.ui.components.neonShadowSoft
import com.rst.player.ui.components.rememberFavoriteIds
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.core.PlayerHeroState
import com.rst.player.ui.theme.BrandAccentGlow
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import com.rst.player.ui.viewmodel.NowPlayingViewModel
import com.rst.player.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(item: NowPlayingItem?, hero: PlayerHeroState, onClose: () -> Unit) {
    val graph = LocalGraph.current
    val vm: NowPlayingViewModel = viewModel {
        NowPlayingViewModel(graph.playerController, graph.musicRepository)
    }
    val playerUi by vm.playerUi.collectAsStateWithLifecycle()
    val localSong by vm.currentSong.collectAsStateWithLifecycle()
    val currentLocalSong = localSong
    val favoriteIds = rememberFavoriteIds(graph)
    val settings by graph.userPreferences.settings
        .collectAsStateWithLifecycle(initialValue = com.rst.player.data.preferences.AppSettings())

    var sleepSheetOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sleepRemaining by vm.sleepRemaining.collectAsStateWithLifecycle()

    val albumColor = rememberAlbumColor(item)
    val baseColor = if (settings.vibrantBackdrop) albumColor else BrandAccent
    val gradient = Brush.verticalGradient(
        listOf(
            lerp(baseColor, Color.Black, 0.45f),
            lerp(baseColor, Color.Black, 0.8f),
            Color.Black
        )
    )

    if (item == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(80.dp))
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color.White)
            }
        }
        BackHandler { onClose() }
        return
    }

    val playScale by animateFloatAsState(
        targetValue = if (playerUi.isPlaying) 1f else 0.88f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "playScale"
    )

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (playPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressScale"
    )

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThreshold = with(density) { 140.dp.toPx() }
    val revealShiftPx = with(density) { 26.dp.toPx() }

    // Shared-element (hero) transition state.
    // fraction: 0 = artwork collapsed onto the mini player, 1 = full screen.
    val fraction = remember { Animatable(0f) }
    var destBounds by remember { mutableStateOf(Rect.Zero) }
    val dragOffset = remember { Animatable(0f) }

    // Begin the hero once the artwork has been measured at its full position.
    LaunchedEffect(destBounds) {
        if (destBounds != Rect.Zero) {
            fraction.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
    }

    fun close() {
        scope.launch {
            fraction.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler { close() }

    val artModel = item.albumId?.let { MediaStoreScanner.albumArtUri(it) } ?: item.artworkUrl

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .graphicsLayer {
                val dragAlpha = (1f - dragOffset.value / screenHeightPx).coerceIn(0f, 1f)
                val openAlpha = FastOutSlowInEasing.transform((fraction.value / 0.25f).coerceIn(0f, 1f))
                alpha = dragAlpha * openAlpha
                translationY = dragOffset.value
            }
    ) {
        if (artModel != null) {
            AsyncImage(
                model = artModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp)
                    .scale(1.2f)
                    .graphicsLayer {
                        alpha = FastOutSlowInEasing.transform((fraction.value / 0.45f).coerceIn(0f, 1f)) * 0.5f
                    }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .then(revealModifier(fraction, 0.20f, 0.45f, revealShiftPx)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { close() }) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                    EqualizerBars(
                        playing = playerUi.isPlaying,
                        tint = BrandAccentBright,
                        barHeight = 12.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { sleepSheetOpen = true }
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = "Sleep timer", tint = Color.White)
                    }
                }
            }

            item {
                Crossfade(targetState = item, label = "artwork") { s ->
                    val artUri = s.albumId?.let { MediaStoreScanner.albumArtUri(it) }
                    val f = fraction.value.coerceIn(0f, 1f)
                    val e = FastOutSlowInEasing.transform(f)
                    val corner = lerp(12.dp, 30.dp, e)
                    val sBounds = hero.miniArtBounds
                    val dBounds = destBounds
                    val hasBounds = sBounds != Rect.Zero && dBounds != Rect.Zero
                    val scaleF = if (hasBounds) lerp(sBounds.width / dBounds.width, 1f, e) else 1f
                    val txF = if (hasBounds) lerp(sBounds.left - dBounds.left, 0f, e) else 0f
                    val tyF = if (hasBounds) lerp(sBounds.top - dBounds.top, 0f, e) else 0f
                    AsyncImage(
                        model = if (artUri != null) artUri else s.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 8.dp)
                            .aspectRatio(1f)
                            .onGloballyPositioned { destBounds = it.boundsInRoot() }
                            .then(
                                if (!hasBounds) Modifier.graphicsLayer { alpha = 0f }
                                else Modifier
                            )
                            .shadow(
                                elevation = 26.dp * e,
                                shape = RoundedCornerShape(corner),
                                clip = false,
                                ambientColor = BrandAccentGlow,
                                spotColor = BrandAccentGlow
                            )
                            .shadow(elevation = 6.dp * e, shape = RoundedCornerShape(corner))
                            .clip(RoundedCornerShape(corner))
                            .graphicsLayer {
                                scaleX = scaleF
                                scaleY = scaleF
                                translationX = txF
                                translationY = tyF
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            if (dragOffset.value >= dismissThreshold) {
                                                coroutineScope {
                                                    launch {
                                                        dragOffset.animateTo(
                                                            screenHeightPx,
                                                            tween(320, easing = FastOutSlowInEasing)
                                                        )
                                                    }
                                                    launch {
                                                        fraction.animateTo(
                                                            0f,
                                                            tween(320, easing = FastOutSlowInEasing)
                                                        )
                                                    }
                                                }
                                                onClose()
                                            } else {
                                                coroutineScope {
                                                    launch {
                                                        dragOffset.animateTo(
                                                            0f,
                                                            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                        )
                                                    }
                                                    launch {
                                                        fraction.animateTo(
                                                            1f,
                                                            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            coroutineScope {
                                                launch {
                                                    dragOffset.animateTo(
                                                        0f,
                                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                    )
                                                }
                                                launch {
                                                    fraction.animateTo(
                                                        1f,
                                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                                            fraction.snapTo(
                                                1f - (dragOffset.value / screenHeightPx).coerceIn(0f, 1f)
                                            )
                                        }
                                    }
                                )
                            }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .then(revealModifier(fraction, 0.32f, 0.60f, revealShiftPx))
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            item {
                Box(modifier = Modifier.then(revealModifier(fraction, 0.45f, 0.75f, revealShiftPx))) {
                    SeekBar(
                        current = playerUi.controller?.currentPosition ?: 0L,
                        duration = playerUi.durationMs,
                        onSeek = { vm.seekTo(it) },
                        isPlaying = playerUi.isPlaying
                    )
                }
            }

            item {
                Box(modifier = Modifier.then(revealModifier(fraction, 0.55f, 0.85f, revealShiftPx))) {
                    Surface(
                        color = Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(30.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { graph.playerController.setShuffle(!playerUi.shuffle) }
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (playerUi.shuffle) BrandAccent else Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = vm::previous, modifier = Modifier.size(54.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                            IconButton(
                                onClick = vm::togglePlay,
                                modifier = Modifier.size(84.dp),
                                interactionSource = playInteraction
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .shadow(20.dp, CircleShape, clip = false, ambientColor = BrandAccentGlow, spotColor = BrandAccentGlow)
                                        .background(Brush.linearGradient(listOf(BrandAccentBright, BrandAccent)), CircleShape)
                                        .graphicsLayer { scaleX = playScale * pressScale; scaleY = playScale * pressScale },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playerUi.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playerUi.isPlaying) "Pause" else "Play",
                                        tint = Color(0xFF0A0A1F),
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                            IconButton(onClick = vm::next, modifier = Modifier.size(54.dp)) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = vm::cycleRepeat) {
                                Icon(
                                    imageVector = if (playerUi.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (playerUi.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) BrandAccent else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .then(revealModifier(fraction, 0.65f, 0.95f, revealShiftPx)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentLocalSong != null) {
                        val isFav = currentLocalSong.id.toString() in favoriteIds
                        IconButton(onClick = { scope.launch { graph.playlistRepository.toggleFavorite(currentLocalSong) } }) {
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) BrandAccent else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        Text(
                            text = "Streaming preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandAccentBright
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (sleepRemaining > 0) {
                        Text(
                            text = "Sleep ${formatDuration(sleepRemaining)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandAccent
                        )
                    }
                }
            }

            if (currentLocalSong != null) {
                item {
                    Box(modifier = Modifier.then(revealModifier(fraction, 0.75f, 1f, revealShiftPx))) {
                        SimilarRow(song = currentLocalSong)
                    }
                }
            }
        }
    }

    if (sleepSheetOpen) {
        ModalBottomSheet(onDismissRequest = { sleepSheetOpen = false }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Sleep timer",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                listOf(5, 10, 15, 30, 60).forEach { minutes ->
                    TextButton(
                        onClick = {
                            vm.setSleepTimer(minutes)
                            sleepSheetOpen = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$minutes minutes", modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(
                    onClick = {
                        vm.cancelSleepTimer()
                        sleepSheetOpen = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel timer", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun revealModifier(reveal: Animatable<Float, *>, start: Float, end: Float, shiftPx: Float): Modifier =
    Modifier.graphicsLayer {
        val t = ((reveal.value - start) / (end - start)).coerceIn(0f, 1f)
        val r = FastOutSlowInEasing.transform(t)
        alpha = r
        translationY = (1f - r) * shiftPx
    }

@Composable
private fun SeekBar(
    current: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean
) {
    var position by remember { mutableLongStateOf(current) }
    var dragging by remember { mutableStateOf(false) }
    val graph = com.rst.player.ui.core.LocalGraph.current

    LaunchedEffect(isPlaying, current) {
        if (!dragging) position = current
        while (isPlaying && isActive) {
            if (!dragging) {
                position = runCatching {
                    graph.playerController.ui.value.controller?.currentPosition ?: 0L
                }.getOrDefault(0L)
            }
            delay(500) // 500 ms is imperceptible with the animation easing below
        }
    }

    val displayValue by animateFloatAsState(
        targetValue = position.toFloat(),
        animationSpec = tween(
            durationMillis = 220,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "seekDisplay"
    )
    val sliderValue = if (dragging) position.toFloat() else displayValue

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Slider(
            value = sliderValue,
            onValueChange = {
                dragging = true
                position = it.toLong()
                // Only seek on drag finish to avoid sending a seek every 16 ms
            },
            onValueChangeFinished = {
                onSeek(position)
                dragging = false
            },
            valueRange = 0f..maxOf(duration, 1L).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = BrandAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(position),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SimilarRow(song: Song) {
    val graph = LocalGraph.current
    var similar by remember(song.id) { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(song.id) {
        similar = withContext(Dispatchers.Default) {
            graph.recommendationEngine.similarTo(song, 10)
        }
    }

    if (similar.isEmpty()) return

    Column {
        Text(
            text = "Similar songs",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(similar, key = { it.id }) { s ->
                Surface(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .width(138.dp)
                        .clickable {
                            val list = listOf(s) + similar.filter { it.id != s.id }
                            graph.playerController.playQueue(list, 0, false)
                        }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        AlbumArt(
                            s.albumId,
                            Modifier
                                .size(122.dp)
                                .neonShadowSoft(RoundedCornerShape(16.dp)),
                            cornerRadius = 16.dp
                        )
                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            text = s.artist,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAlbumColor(item: NowPlayingItem?): Color {
    val fallback = MaterialTheme.colorScheme.background
    val context = androidx.compose.ui.platform.LocalContext.current
    var color by remember(item) { mutableStateOf(fallback) }
    LaunchedEffect(item) {
        if (item == null) return@LaunchedEffect
        val data: Any? = item.albumId?.let { MediaStoreScanner.albumArtUri(it) } ?: item.artworkUrl
        if (data == null) return@LaunchedEffect
        try {
            // Cap the bitmap at 200×200 before color averaging to prevent OOM / JNI freeze
            // on large album art (e.g. 2000×2000 images).
            val request = coil.request.ImageRequest.Builder(context)
                .data(data)
                .size(200, 200)
                .allowHardware(false)
                .build()
            val loader = coil.Coil.imageLoader(context)
            val result = loader.execute(request)
            val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bmp != null) color = averageColor(bmp)
        } catch (_: Exception) {
        }
    }
    return color
}

private fun averageColor(bitmap: android.graphics.Bitmap): Color {
    val step = maxOf(1, bitmap.width / 12)
    var r = 0f
    var g = 0f
    var b = 0f
    var n = 0
    var x = 0
    while (x < bitmap.width) {
        var y = 0
        while (y < bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            r += android.graphics.Color.red(pixel)
            g += android.graphics.Color.green(pixel)
            b += android.graphics.Color.blue(pixel)
            n++
            y += step
        }
        x += step
    }
    if (n == 0) return Color(0xFF181818)
    return Color(
        red = r / n / 255f,
        green = g / n / 255f,
        blue = b / n / 255f
    )
}
