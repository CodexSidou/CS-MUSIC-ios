package com.rst.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rst.player.ui.theme.BrandAccentGlow
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ~2.5s neon splash shown on app launch. Black canvas, a soft green
 * glow breathing in, the RST letters springing up one by one with a
 * neon halo, a radar ring ping, a gradient underline sweep, then the
 * whole thing scales up and fades away into the app.
 */
@Composable
fun AppIntroOverlay(onFinished: () -> Unit) {
    val glow = remember { Animatable(0f) }
    val letterK = remember { Animatable(0f) }
    val letterI = remember { Animatable(0f) }
    val letterZ = remember { Animatable(0f) }
    val sub = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    val scale = remember { Animatable(0.86f) }
    val fadeOut = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        glow.animateTo(1f, tween(450, easing = LinearEasing))

        launch {
            letterK.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
        delay(130)
        launch {
            letterI.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
        delay(130)
        launch {
            letterZ.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }

        launch {
            delay(180)
            ring.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }

        delay(430)

        launch {
            sub.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            sweep.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        delay(620)

        launch { scale.animateTo(1.06f, tween(430, easing = FastOutSlowInEasing)) }
        fadeOut.animateTo(0f, tween(430, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .graphicsLayer {
                alpha = fadeOut.value
                scaleX = scale.value
                scaleY = scale.value
            }
    ) {
        // Breathing green aura behind the wordmark.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(430.dp)
                .graphicsLayer { alpha = glow.value * 0.55f }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(BrandAccent.copy(alpha = 0.30f), Color.Transparent)
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(150.dp + 330.dp * ring.value)
                .graphicsLayer { alpha = (1f - ring.value) * 0.9f }
                .clip(CircleShape)
                .border(2.dp, BrandAccent.copy(alpha = 0.55f), CircleShape)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                IntroLetter("K", letterK)
                IntroLetter("I", letterI)
                IntroLetter("Z", letterZ)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(3.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .width(200.dp * sweep.value)
                        .height(3.dp)
                        .graphicsLayer { alpha = sweep.value }
                        .shadow(10.dp, RoundedCornerShape(2.dp), ambientColor = BrandAccentGlow, spotColor = BrandAccentGlow)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(BrandAccent, BrandAccentBright, BrandAccent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PLAYER",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.65f)
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = sub.value
                    translationY = (1f - sub.value) * 14f
                }
            )
        }
    }
}

@Composable
private fun IntroLetter(letter: String, reveal: Animatable<Float, *>) {
    Text(
        text = letter,
        style = TextStyle(
            fontSize = 76.sp,
            fontWeight = FontWeight.Black,
            color = BrandAccentBright,
            shadow = Shadow(BrandAccentGlow, blurRadius = 26f)
        ),
        modifier = Modifier.graphicsLayer {
            val v = reveal.value
            alpha = v
            scaleX = 0.5f + 0.5f * v
            scaleY = 0.5f + 0.5f * v
            translationY = (1f - v) * 54f
        }
    )
}
