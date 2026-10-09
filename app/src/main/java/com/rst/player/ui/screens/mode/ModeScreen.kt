package com.rst.player.ui.screens.mode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.rst.player.data.mediastore.MediaStoreScanner
import com.rst.player.data.model.PlayerMode
import com.rst.player.data.model.Song
import com.rst.player.data.repository.ModeRepository
import com.rst.player.ui.components.AlbumArt
import com.rst.player.ui.components.EqualizerBars
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.viewmodel.ModeViewModel
import com.rst.player.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun ModeScreen(
    modeId: String,
    onExit: () -> Unit,
    onCustomizePlaylist: (PlayerMode) -> Unit
) {
    val graph = LocalGraph.current
    val vm: ModeViewModel = viewModel(key = "mode-$modeId") {
        ModeViewModel(
            modeId,
            graph.modeRepository,
            graph.musicRepository,
            graph.historyRepository,
            graph.playlistRepository,
            graph.moodRepository
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val playerUi by graph.playerController.ui.collectAsStateWithLifecycle()
    val modes by graph.modeRepository.modes.collectAsStateWithLifecycle()
    val mode = modes.find { it.id == modeId }

    if (mode == null) {
        LaunchedEffect(Unit) { onExit() }
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val visuals = visualsFor(mode)
    val playlistName = ModeRepository.playlistNameFor(mode)

    var introDone by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val queue = state.queue
    var autoStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.loading, introDone) {
        if (introDone && !state.loading && queue.isNotEmpty() && !autoStarted) {
            autoStarted = true
            val ui = graph.playerController.ui.value
            val resumeIndex = queue.indexOfFirst { it.id.toString() == ui.currentMediaId }
            if (resumeIndex >= 0 && ui.playbackState != Player.STATE_IDLE) {
                val resumePosition = ui.controller?.currentPosition ?: 0L
                graph.playerController.playQueue(queue, resumeIndex, false, startPositionMs = resumePosition)
            } else {
                graph.playerController.playQueue(queue, 0, false)
            }
        }
    }

    val currentSong = queue.find { it.id.toString() == playerUi.currentMediaId }
    val currentIndex = queue.indexOfFirst { it.id.toString() == playerUi.currentMediaId }

    BackHandler(onBack = { showExitDialog = true })

    fun exitToNormal() {
        showExitDialog = false
        onExit()
    }

    fun exitAndStop() {
        graph.playerController.stop()
        showExitDialog = false
        onExit()
    }

    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xFF0E1F17),
            title = { Text("Exit ${mode.name.lowercase()} mode?", color = Color.White, fontWeight = FontWeight.SemiBold) },
            text = { Text("What should happen with the music?", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = ::exitToNormal) {
                    Text("Switch to player", color = visuals.accentBright)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = ::exitAndStop) {
                    Text("Stop music", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    val albumColor = rememberAlbumColor(currentSong)
    val baseColor = if (currentSong == null) visuals.accent else albumColor
    val gradient = Brush.verticalGradient(
        listOf(
            lerp(baseColor, Color.Black, 0.35f),
            lerp(baseColor, Color.Black, 0.72f),
            Color.Black
        )
    )

    val subtitle = when {
        state.manualEmpty -> "No songs yet — customize below"
        state.isManual -> "Custom playlist · ${state.totalCount} tracks"
        else -> smartSubtitleFor(mode)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        AmbientGlow(color = visuals.accent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showExitDialog = true }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Exit", tint = Color.White)
                }
                EqualizerBars(playing = playerUi.isPlaying, tint = visuals.accentBright, barHeight = 12.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modeDisplayTitle(mode),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.sp,
                        color = visuals.accentBright
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                if (state.totalCount > 0) {
                    Surface(
                        color = visuals.accent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, visuals.accent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${state.totalCount} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = visuals.accentBright,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.loading -> {
                        CircularProgressIndicator(
                            color = visuals.accent,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    state.manualEmpty -> {
                        ManualEmptyState(
                            visuals = visuals,
                            playlistName = playlistName,
                            onCustomize = { onCustomizePlaylist(mode) },
                            onUseAutoMix = {
                                scope.launch { graph.modeRepository.setPlaylist(mode.id, null) }
                            }
                        )
                    }
                    queue.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                visuals.icon,
                                contentDescription = null,
                                tint = visuals.accent.copy(alpha = 0.7f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Nothing to play yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add some music to your library first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        ModeContent(
                            visuals = visuals,
                            song = currentSong,
                            queue = queue,
                            currentIndex = currentIndex,
                            total = queue.size,
                            isPlaying = playerUi.isPlaying,
                            isManual = state.isManual,
                            onToggle = { graph.playerController.togglePlay() },
                            onNext = { graph.playerController.next() },
                            onPrevious = { graph.playerController.previous() },
                            onPlayAt = { index -> graph.playerController.playAt(index) },
                            onCustomize = { onCustomizePlaylist(mode) }
                        )
                    }
                }
            }
        }

        // Intro overlay LAST so it renders on top of everything
        AnimatedVisibility(
            visible = !introDone,
            exit = fadeOut(tween(500))
        ) {
            ModeIntro(mode = mode, visuals = visuals, onFinished = { introDone = true })
        }
    }
}

@Composable
private fun BoxScope.ManualEmptyState(
    visuals: ModeVisuals,
    playlistName: String,
    onCustomize: () -> Unit,
    onUseAutoMix: () -> Unit
) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            visuals.icon,
            contentDescription = null,
            tint = visuals.accent.copy(alpha = 0.7f),
            modifier = Modifier.size(72.dp)
        )
        Text(
            text = "No songs in this mode yet",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Long-press any song and add it to the \"$playlistName\" playlist, or pick music here.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(visuals.accentBright, visuals.accent)))
                .clickable(onClick = onCustomize)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color(0xFF0A0A1F), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Customize music", color = Color(0xFF0A0A1F), fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(onClick = onUseAutoMix) {
            Text("Use auto mix instead", color = visuals.accentBright)
        }
    }
}

// ── Cinematic intro (one consistent sequence, recolored per mode) ────────

@Composable
private fun ModeIntro(mode: PlayerMode, visuals: ModeVisuals, onFinished: () -> Unit) {
    val ringScale = remember { Animatable(0.3f) }
    val ringAlpha = remember { Animatable(0f) }
    val iconProgress = remember { Animatable(0f) }
    val textProgress = remember { Animatable(0f) }
    val sweepProgress = remember { Animatable(0f) }
    val subAlpha = remember { Animatable(0f) }

    var fading by remember { mutableStateOf(false) }
    val fadeOut = animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = tween(450),
        label = "modeIntroFade"
    )

    LaunchedEffect(Unit) {
        launch {
            ringAlpha.animateTo(0.7f, tween(300))
            launch { ringScale.animateTo(1f, tween(500, easing = LinearEasing)) }
            launch { ringAlpha.animateTo(0f, tween(500, delayMillis = 300)) }
        }
        launch {
            delay(100)
            iconProgress.animateTo(1f, spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ))
        }
        delay(400)
        textProgress.animateTo(1f, spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ))
        launch {
            delay(300)
            subAlpha.animateTo(1f, tween(500))
        }
        launch {
            delay(200)
            sweepProgress.animateTo(1f, tween(1200, easing = LinearEasing))
        }
        delay(1200)
        fading = true
        delay(500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fadeOut.value }
            .background(
                Brush.verticalGradient(
                    listOf(lerp(visuals.accent, Color.Black, 0.55f), Color.Black)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = sweepProgress.value * 0.18f }
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            visuals.accentBright.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        startX = -300f + sweepProgress.value * 1400f,
                        endX = sweepProgress.value * 1400f
                    )
                )
        )

        for (i in 0..5) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .height(1.dp)
                    .graphicsLayer {
                        translationY = (i - 2.5f) * 70f
                        alpha = sweepProgress.value * 0.12f * (1f - kotlin.math.abs(i - 2.5f) / 3f)
                    }
                    .background(visuals.accentBright)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(180.dp)
                .graphicsLayer {
                    scaleX = ringScale.value
                    scaleY = ringScale.value
                    alpha = ringAlpha.value
                }
                .clip(CircleShape)
                .border(3.dp, visuals.accentBright, CircleShape)
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        val s = iconProgress.value
                        scaleX = s; scaleY = s
                        alpha = iconProgress.value
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(visuals.accentBright.copy(alpha = 0.3f), Color.Transparent)
                        )
                    )
                    .border(2.dp, visuals.accentBright.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    visuals.icon,
                    contentDescription = null,
                    tint = visuals.accentBright,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = (1f - textProgress.value) * 40f
                        alpha = textProgress.value
                    }
            ) {
                Text(
                    text = modeDisplayTitle(mode),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp
                    ),
                    color = visuals.accentBright
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = introSubtitleFor(mode),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = subAlpha.value * 0.7f),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun BoxScope.AmbientGlow(color: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(380.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.22f), Color.Transparent)
                )
            )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeContent(
    visuals: ModeVisuals,
    song: Song?,
    queue: List<Song>,
    currentIndex: Int,
    total: Int,
    isPlaying: Boolean,
    isManual: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onCustomize: () -> Unit
) {
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
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

    // HorizontalPager: swipeable album art that shows adjacent songs
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceAtLeast(0),
        pageCount = { total.coerceAtLeast(1) }
    )

    var firstSync by remember { mutableStateOf(true) }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            val target = currentIndex.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
            if (firstSync) {
                firstSync = false
                pagerState.scrollToPage(target)
            } else if (target != pagerState.currentPage) {
                if (kotlin.math.abs(target - pagerState.currentPage) > 1) {
                    pagerState.scrollToPage(target)
                } else {
                    pagerState.animateScrollToPage(target)
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val target = pagerState.currentPage
        if (target != currentIndex && target >= 0 && target < queue.size) {
            onPlayAt(target)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            key = { queue.getOrNull(it)?.id ?: it.toLong() }
        ) { page ->
            val s = queue.getOrNull(page)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (s == null) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 150.dp, max = 264.dp)
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                } else {
                    AlbumArt(
                        albumId = s.albumId,
                        modifier = Modifier
                            .widthIn(min = 150.dp, max = 264.dp)
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f)
                            .shadow(26.dp, RoundedCornerShape(28.dp), clip = false, ambientColor = visuals.glow, spotColor = visuals.glow)
                            .shadow(6.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .graphicsLayer {
                                val s2 = if (isPlaying && page == pagerState.currentPage) 1f else 0.97f
                                scaleX = s2; scaleY = s2
                            },
                        cornerRadius = 28.dp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = s?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = s?.artist ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (total > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentIndex >= 0) "Now playing · ${currentIndex + 1} of $total" else "Queue ready · $total tracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = visuals.accentBright.copy(alpha = 0.9f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ModeSeekBar(accent = visuals.accentBright)

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(104.dp),
                interactionSource = playInteraction
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(44.dp, CircleShape, clip = false, ambientColor = visuals.glow, spotColor = visuals.glow)
                        .background(Brush.linearGradient(listOf(visuals.accentBright, visuals.accent)), CircleShape)
                        .graphicsLayer { scaleX = playScale * pressScale; scaleY = playScale * pressScale },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0A0A1F),
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        if (currentIndex >= 0) {
            Spacer(modifier = Modifier.height(14.dp))
            NextUpRail(
                queue = queue,
                currentIndex = currentIndex,
                accentBright = visuals.accentBright,
                onPlayAt = onPlayAt
            )
        }

        TextButton(
            onClick = onCustomize,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
        ) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = null,
                tint = visuals.accentBright,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isManual) "Edit playlist" else "Customize music",
                color = visuals.accentBright
            )
        }
    }
}

@Composable
private fun NextUpRail(
    queue: List<Song>,
    currentIndex: Int,
    accentBright: Color,
    onPlayAt: (Int) -> Unit
) {
    val upcoming = queue.drop(currentIndex + 1).take(8)
    if (upcoming.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "NEXT UP · ${upcoming.size} TRACKS",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = accentBright,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(upcoming, key = { _, song -> song.id }) { i, song ->
                val realIndex = currentIndex + 1 + i
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .width(168.dp)
                        .clickable { onPlayAt(realIndex) }
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArt(
                            song.albumId,
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            cornerRadius = 10.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSeekBar(accent: Color) {
    val graph = LocalGraph.current
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    val duration = graph.playerController.ui.value.durationMs
    val isPlaying = graph.playerController.ui.value.isPlaying

    LaunchedEffect(isPlaying) {
        while (isActive) {
            if (!dragging) {
                position = runCatching {
                    graph.playerController.ui.value.controller?.currentPosition ?: 0L
                }.getOrDefault(0L)
            }
            delay(500)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Slider(
            value = position.toFloat().coerceIn(0f, maxOf(duration, 1L).toFloat()),
            onValueChange = {
                dragging = true
                position = it.toLong()
            },
            onValueChangeFinished = {
                graph.playerController.seekTo(position)
                dragging = false
            },
            valueRange = 0f..maxOf(duration, 1L).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth(0.9f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(position),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun rememberAlbumColor(song: Song?): Color {
    val fallback = Color(0xFF0C3B24)
    val context = androidx.compose.ui.platform.LocalContext.current
    var color by remember(song) { mutableStateOf(fallback) }
    LaunchedEffect(song) {
        if (song == null) return@LaunchedEffect
        val uri = MediaStoreScanner.albumArtUri(song.albumId) ?: return@LaunchedEffect
        try {
            val request = coil.request.ImageRequest.Builder(context)
                .data(uri)
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
