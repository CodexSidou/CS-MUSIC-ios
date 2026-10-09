package com.rst.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun EqualizerBars(
    playing: Boolean,
    tint: Color,
    barHeight: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = listOf(0f, 1.2f, 0.5f, 1.8f)
        // The infinite transition is only created while audio is playing. When the
        // app is idle/paused, the bars are static — otherwise a 60 fps animation
        // keeps recomposing every list row / header that shows them and the whole
        // app feels heavy even when nothing is moving.
        val transition = if (playing) rememberInfiniteTransition(label = "eq") else null
        bars.forEachIndexed { index, _ ->
            val animated = if (transition != null) {
                transition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 480,
                            delayMillis = (index * 120),
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "eq$index"
                ).value
            } else {
                0.45f
            }
            // The group structure never changes between playing/paused so the slot
            // table stays stable across recompositions (avoids Compose runtime
            // corruption when this component lives inside a LazyList item).
            val scale = animated
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .background(tint, RoundedCornerShape(2.dp))
                    // CompositingStrategy.Offscreen renders to a hardware layer so the
                    // GPU composites the bar each frame instead of re-drawing from scratch.
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 1f)
                    }
            )
        }
    }
}
