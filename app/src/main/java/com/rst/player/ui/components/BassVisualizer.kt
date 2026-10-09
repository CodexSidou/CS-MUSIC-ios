package com.rst.player.ui.components

import android.media.audiofx.Visualizer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live bass energy for a playback surface.
 *
 * Tries to attach a real [Visualizer] FFT to the ExoPlayer audio session so
 * the bars genuinely follow the music's low end. If the audio session is
 * unavailable or RECORD_AUDIO is denied (Android 10+), it gracefully falls
 * back to a smooth organic pulse that still dances while audio plays.
 *
 * Both [BassReactive.level] and [BassReactive.phase] are only read from
 * draw-phase lambdas (drawBehind / graphicsLayer), so updating them never
 * recomposes the host UI — the bars redraw directly on the GPU layer.
 */
class BassReactive {
    val level = mutableFloatStateOf(0f)
    val phase = mutableFloatStateOf(0f)
}

@Composable
fun rememberBassReactive(isPlaying: Boolean, audioSessionId: Int = 0): BassReactive {
    val reactive = remember { BassReactive() }
    val envelope = remember { floatArrayOf(0f) }

    var visualizer by remember { mutableStateOf<Visualizer?>(null) }

    DisposableEffect(audioSessionId, isPlaying) {
        var vis: Visualizer? = null
        if (isPlaying && audioSessionId > 0) {
            vis = runCatching {
                Visualizer(audioSessionId).apply {
                    setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) = Unit

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) {
                                val data = fft ?: return
                                reactive.phase.floatValue += 0.14f
                                val e = bassEnergy(data)
                                // Fast attack, slow decay — the classic "pump".
                                envelope[0] = if (e > envelope[0]) {
                                    envelope[0] + (e - envelope[0]) * 0.55f
                                } else {
                                    envelope[0] * 0.93f
                                }
                                reactive.level.floatValue = envelope[0]
                            }
                        },
                        0, false, true
                    )
                    enabled = true
                }
            }.getOrNull()
        }
        visualizer = vis
        onDispose {
            runCatching { vis?.enabled = false }
            vis?.release()
            visualizer = null
        }
    }

    // Fallback: no visualizer (no permission / no session yet) — organic pulse.
    LaunchedEffect(isPlaying, visualizer) {
        if (isPlaying && visualizer == null) {
            var t = 0f
            while (true) {
                t += 0.10f
                reactive.phase.floatValue += 0.10f
                val target = 0.28f + 0.42f * (0.5f + 0.5f * sin(t * 0.85f))
                envelope[0] += (target - envelope[0]) * 0.20f
                reactive.level.floatValue = envelope[0]
                delay(46)
            }
        }
    }

    // Idle: settle the bars to zero with a smooth release.
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            while (reactive.level.floatValue > 0.012f) {
                reactive.level.floatValue *= 0.88f
                envelope[0] = reactive.level.floatValue
                delay(30)
            }
            reactive.level.floatValue = 0f
            envelope[0] = 0f
        }
    }

    return reactive
}

private fun bassEnergy(fft: ByteArray): Float {
    val bins = minOf(14, fft.size / 2 - 1)
    var sum = 0f
    for (i in 1..bins) {
        val re = fft[i * 2].toFloat() / 128f
        val im = fft[i * 2 + 1].toFloat() / 128f
        sum += sqrt(re * re + im * im)
    }
    return (sum / bins).coerceIn(0f, 1f)
}

@Composable
fun BassBars(
    reactive: BassReactive,
    barCount: Int = 7,
    maxHeight: Dp = 12.dp,
    barWidth: Dp = 3.dp,
    spacing: Dp = 2.5.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val maxPx = with(density) { maxHeight.toPx() }
    val barPx = with(density) { barWidth.toPx() }
    val gapPx = with(density) { spacing.toPx() }
    val totalW = barWidth * barCount + spacing * (barCount - 1)
    val brush = remember { Brush.verticalGradient(listOf(BrandAccentBright, BrandAccent)) }
    val weights = remember { FloatArray(barCount) { 0.45f + 0.55f * abs(sin(it * 1.31f + 0.4f)) } }
    val phases = remember { FloatArray(barCount) { it * 1.85f } }

    Box(
        modifier = modifier
            .size(totalW, maxHeight)
            .drawBehind {
                val lvl = reactive.level.floatValue
                val ph = reactive.phase.floatValue
                var x = 0f
                for (i in 0 until barCount) {
                    val wob = 0.72f + 0.28f * abs(sin(ph * 2.0f + phases[i]))
                    val h = maxPx * lvl * weights[i] * wob
                    if (h > 0.6f) {
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(x, size.height - h),
                            size = Size(barPx, h),
                            cornerRadius = CornerRadius(barPx / 2f, barPx / 2f)
                        )
                    }
                    x += barPx + gapPx
                }
            }
    )
}
