package com.rst.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rst.player.ui.theme.BrandAccentGlow
import com.rst.player.ui.theme.BrandAccent

/** Brand-green ambient glow cast behind rounded surfaces. */
fun Modifier.neonShadow(
    shape: Shape,
    alpha: Float = 0.55f,
    elevation: Dp = 18.dp
): Modifier = this.then(
    Modifier.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = BrandAccentGlow.copy(alpha = alpha),
        spotColor = BrandAccentGlow.copy(alpha = alpha)
    )
)

/** Cheap, faint glow for scrolling list items — far cheaper to rasterize per frame. */
fun Modifier.neonShadowSoft(
    shape: Shape,
    alpha: Float = 0.20f,
    elevation: Dp = 5.dp
): Modifier = this.then(
    Modifier.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = BrandAccentGlow.copy(alpha = alpha),
        spotColor = BrandAccentGlow.copy(alpha = alpha)
    )
)

/** Soft vertical scrim laid over artwork so titles stay legible. */
@Composable
fun BoxScope.GlassScrim(alpha: Float = 0.62f) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha)))
            )
    )
}

/** Neon ring that marks the currently-playing card. */
@Composable
fun BoxScope.GlowRing(
    color: Color = BrandAccent,
    alpha: Float = 0.95f,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .border(2.dp, color.copy(alpha = alpha), shape)
    )
}

/** Hairline glass border for translucent "future" surfaces. */
fun Modifier.glassBorder(
    shape: Shape,
    alpha: Float = 0.14f,
    width: Dp = 1.dp
): Modifier = this.then(Modifier.border(width, Color.White.copy(alpha = alpha), shape))
