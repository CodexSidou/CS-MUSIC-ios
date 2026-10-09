package com.rst.player.ui.components.backgrounds

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rst.player.data.preferences.AppSettings
import com.rst.player.ui.theme.BrandAccentGlow
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAU = (2.0 * PI).toFloat()

/**
 * Full-bleed animated backdrop rendered behind the main tabs. A single looping
 * phase drives every style, so the composition stays lightweight and the
 * per-frame work is limited to a handful of Canvas draw calls.
 */
@Composable
fun AnimatedAppBackground(style: Int, modifier: Modifier = Modifier) {
    if (style == AppSettings.BACKGROUND_NONE) return

    val transition = rememberInfiniteTransition(label = "appBackground")
    val phaseState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val t = phaseState.value
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF0A1412), Color(0xFF04060B))
            )
        )
        when (style) {
            AppSettings.BACKGROUND_AURORA -> drawAurora(t)
            AppSettings.BACKGROUND_WAVES -> drawWaves(t)
            AppSettings.BACKGROUND_PARTICLES -> drawParticles(t)
            AppSettings.BACKGROUND_PULSE -> drawPulse(t)
        }
    }
}

private fun DrawScope.drawAurora(t: Float) {
    val w = size.width
    val h = size.height
    val blobs = listOf(
        Triple(0f, BrandAccentBright, 0.16f),
        Triple(2.1f, Color(0xFF7C5CFF), 0.13f),
        Triple(4.2f, Color(0xFF2EC4B6), 0.12f),
        Triple(1.4f, Color(0xFFFF8A3D), 0.08f)
    )
    blobs.forEachIndexed { i, (phase, color, alpha) ->
        val cx = w * (0.5f + 0.42f * sin(TAU * (t + phase * 0.35f)))
        val cy = h * (0.5f + 0.38f * cos(TAU * (t * 0.7f + phase * 0.5f)))
        val radius = min(w, h) * (0.55f + 0.25f * sin(TAU * (t + phase * 0.25f)))
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.45f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

private fun DrawScope.drawWaves(t: Float) {
    val w = size.width
    val h = size.height
    val layers = listOf(
        Triple(0.16f, 0.24f, BrandAccentBright.copy(alpha = 0.16f)),
        Triple(0.11f, 0.17f, Color(0xFF2EC4B6).copy(alpha = 0.13f)),
        Triple(0.07f, 0.11f, Color(0xFF7C5CFF).copy(alpha = 0.10f))
    )
    val step = 20.dp.toPx()
    layers.forEachIndexed { i, (amplitude, speed, color) ->
        val phaseOffset = i * 1.7f
        val path = Path()
        var x = -step
        val baseY = h * 0.5f + amplitude * h * sin(TAU * t * speed + phaseOffset)
        path.moveTo(0f, baseY)
        while (x <= w + step) {
            val y = h * 0.5f + amplitude * h * sin(x / w * TAU * 2f + TAU * t * speed + phaseOffset)
            path.lineTo(x, y)
            x += step
        }
        path.lineTo(w, baseY)
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
    }
}

private fun DrawScope.drawParticles(t: Float) {
    val w = size.width
    val h = size.height
    val count = 30
    for (i in 0 until count) {
        val seed = i * 2654435761L
        val baseX = ((seed * 31) % 997) / 997f
        val speed = 0.10f + ((seed * 17) % 61) / 320f
        val sizePx = 1.2f + ((seed * 13) % 40) / 30f * 1.6f
        val swayPhase = ((seed * 7) % 628) / 100f
        val glow = 0.5f + 0.5f * sin(TAU * (t * 1.6f + swayPhase))
        val y = h * (1f - ((t * speed + (seed * 0.00037f) % 1f) % 1f))
        val x = w * (baseX + 0.03f * sin(TAU * (t * 0.4f + swayPhase)))
        drawCircle(
            color = BrandAccentBright.copy(alpha = 0.05f + 0.12f * glow),
            radius = sizePx,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawPulse(t: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.42f
    val center = Offset(cx, cy)
    for (k in 0 until 4) {
        val local = (t + k / 4f) % 1f
        val radius = local * max(w, h) * 0.55f
        drawCircle(
            color = BrandAccentGlow.copy(alpha = (1f - local) * 0.10f),
            radius = radius,
            center = center
        )
    }
    val coreGlow = 0.5f + 0.5f * sin(TAU * t)
    val coreRadius = min(w, h) * 0.35f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                BrandAccentBright.copy(alpha = 0.16f + 0.10f * coreGlow),
                Color.Transparent
            ),
            center = center,
            radius = coreRadius
        ),
        radius = coreRadius,
        center = center
    )
}
