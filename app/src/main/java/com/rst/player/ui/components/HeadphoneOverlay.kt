package com.rst.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rst.player.ui.theme.BrandAccentGlow
import com.rst.player.ui.theme.BrandAccentBright
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * System-level "device connected" experience: a full-screen dimmed scrim
 * with a vignette, a breathing green aura, soft expanding pulse rings, a
 * large device icon popping in, then the title / device name / status
 * text revealing in a staggered cascade. Stays up exactly 3 seconds,
 * then the content scales and fades away before the scrim lifts.
 */
@Composable
fun HeadphoneOverlay(state: HeadphoneOverlayState) {
    var composed by remember { mutableStateOf(false) }
    val scrimAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.94f) }

    // Enter: fade the scrim in, settle the content, hold exactly 3 s, dismiss.
    LaunchedEffect(state.showCount) {
        if (state.visible) {
            val fresh = !composed
            composed = true
            if (fresh) {
                contentScale.snapTo(0.94f)
                contentAlpha.snapTo(0f)
            }
            launch { scrimAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            launch { contentAlpha.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
            launch {
                contentScale.animateTo(
                    1f,
                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                )
            }
            delay(3000)
            state.hide()
        }
    }

    // Exit: content fades/scales away first, the scrim lingers a beat
    // longer, only then the overlay unmounts — no abrupt cut.
    LaunchedEffect(state.visible) {
        if (!state.visible && composed) {
            launch { contentAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
            launch { contentScale.animateTo(0.95f, tween(300, easing = FastOutSlowInEasing)) }
            scrimAlpha.animateTo(0f, tween(440, easing = FastOutSlowInEasing))
            composed = false
        }
    }

    if (composed) {
        Box(modifier = Modifier.fillMaxSize()) {
            ConnectionScrim(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha.value }
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ConnectionContent(
                    deviceName = state.deviceName,
                    modifier = Modifier.graphicsLayer {
                        alpha = contentAlpha.value
                        scaleX = contentScale.value
                        scaleY = contentScale.value
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectionScrim(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF03050A).copy(alpha = 0.86f))
        )
        // Vignette keeps the focus on the center and gives the dim real depth.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.48f),
                        center = Offset(w * 0.5f, h * 0.44f),
                        radius = maxOf(w, h) * 0.78f
                    )
                )
        )
    }
}

@Composable
private fun ConnectionContent(
    deviceName: String,
    modifier: Modifier = Modifier
) {
    // Play the entrance exactly once per mount: keying these on the show
    // counter made them reset (blink out) when Android fires a duplicate
    // device-added callback mid-show. Re-shows while composed are deduped
    // in HeadphoneOverlayState, and a fresh mount replays the stagger.
    val icon = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val subtitle = remember { Animatable(0f) }
    val status = remember { Animatable(0f) }

    // Breathing aura + two staggered soft pulse rings, all infinite.
    val breath = rememberInfiniteTransition(label = "hpBreath")
    val breathAlpha by breath.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "hpGlow"
    )
    val ringScale1 by breath.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "hpRing1S"
    )
    val ringAlpha1 by breath.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "hpRing1A"
    )
    val ringScale2 by breath.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing, delayMillis = 575)),
        label = "hpRing2S"
    )
    val ringAlpha2 by breath.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing, delayMillis = 575)),
        label = "hpRing2A"
    )

    // Staggered reveal: icon pops first, then title, device, status.
    LaunchedEffect(Unit) {
        launch {
            delay(80)
            icon.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
        launch { title.animateTo(1f, tween(420, easing = FastOutSlowInEasing, delayMillis = 170)) }
        launch { subtitle.animateTo(1f, tween(400, easing = FastOutSlowInEasing, delayMillis = 290)) }
        launch { status.animateTo(1f, tween(360, easing = FastOutSlowInEasing, delayMillis = 400)) }
    }

    Column(
        modifier = modifier
            .widthIn(max = 340.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(230.dp),
            contentAlignment = Alignment.Center
        ) {
            // Soft ambient aura behind everything.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = breathAlpha }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BrandAccentGlow.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )
            PulsingRing(scale = ringScale1, fade = ringAlpha1)
            PulsingRing(scale = ringScale2, fade = ringAlpha2)
            // Icon disc.
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        val s = icon.value
                        scaleX = s
                        scaleY = s
                        alpha = icon.value
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BrandAccentBright.copy(alpha = 0.32f), Color.Transparent)
                        )
                    )
                    .border(1.5.dp, BrandAccentBright.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = deviceIcon(deviceName),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Connected",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White
            ),
            modifier = Modifier.graphicsLayer {
                alpha = title.value
                translationY = (1f - title.value) * 16f
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = deviceName,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.90f)
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                alpha = subtitle.value
                translationY = (1f - subtitle.value) * 14f
            }
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { alpha = status.value }
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer { alpha = 0.55f + breathAlpha * 0.45f }
                    .clip(CircleShape)
                    .background(BrandAccentBright)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = "Ready to play",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = BrandAccentBright
                )
            )
        }
    }
}

@Composable
private fun PulsingRing(scale: Float, fade: Float) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = fade
            }
            .border(2.dp, BrandAccentBright.copy(alpha = 0.45f), CircleShape)
    )
}

@Composable
private fun deviceIcon(name: String) = when {
    name.contains("USB", ignoreCase = true) -> Icons.Rounded.Cable
    name.contains("Bluetooth", ignoreCase = true) || name.contains("LE", ignoreCase = true) ->
        Icons.Rounded.Bluetooth
    else -> Icons.Rounded.Headphones
}
