package com.rst.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rst.player.player.NowPlayingItem
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.core.PlayerHeroState
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MiniPlayer(
    item: NowPlayingItem?,
    hero: PlayerHeroState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (item == null) return

    val graph = LocalGraph.current
    val playerUi by graph.playerController.ui.collectAsStateWithLifecycle()
    val isPlaying = playerUi.isPlaying
    val bass = rememberBassReactive(
        isPlaying = isPlaying,
        audioSessionId = (playerUi.controller as? androidx.media3.exoplayer.ExoPlayer)?.audioSessionId ?: 0
    )
    var position by remember { mutableLongStateOf(0L) }

    fun safePosition(): Long = runCatching {
        graph.playerController.ui.value.controller?.currentPosition ?: 0L
    }.getOrDefault(0L)

    LaunchedEffect(isPlaying, item.mediaId) {
        position = safePosition()
        while (isPlaying && isActive) {
            position = safePosition()
            delay(500)
        }
    }

    val progress = if (playerUi.durationMs > 0) {
        (position.toFloat() / playerUi.durationMs).coerceIn(0f, 1f)
    } else 0f

    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.88f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "playScale"
    )

    val corner = RoundedCornerShape(22.dp)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = corner,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = corner,
                clip = false,
                ambientColor = BrandAccent.copy(alpha = 0.12f),
                spotColor = BrandAccent.copy(alpha = 0.25f)
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, BrandAccent.copy(alpha = 0.30f), Color.Transparent)
                ),
                shape = corner
            )
            .clickable(onClick = onOpen)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(58.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .graphicsLayer {
                                val b = bass.level.floatValue
                                scaleX = 1f + b * 0.12f
                                scaleY = 1f + b * 0.12f
                                alpha = 0.45f + b * 0.30f
                            }
                            .background(
                                Brush.radialGradient(
                                    listOf(BrandAccent.copy(alpha = 0.50f), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .onGloballyPositioned { hero.miniArtBounds = it.boundsInRoot() }
                    ) {
                        Crossfade(targetState = item, label = "miniArt") { s ->
                            if (s.albumId != null) {
                                AlbumArt(s.albumId, Modifier.size(46.dp), cornerRadius = 12.dp)
                            } else {
                                RemoteArt(s.artworkUrl, Modifier.size(46.dp), cornerRadius = 12.dp)
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPlaying) {
                            Spacer(modifier = Modifier.width(6.dp))
                            BassBars(reactive = bass, maxHeight = 12.dp)
                        }
                    }
                    Text(
                        text = item.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(BrandAccent.copy(alpha = 0.30f), Color.Transparent)
                                    ),
                                    CircleShape
                                )
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(
                            onClick = onToggle,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = playScale
                                        scaleY = playScale
                                    }
                            )
                        }
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 10.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(BrandAccent, BrandAccentBright)))
                )
            }
        }
    }
}
