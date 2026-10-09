package com.rst.player.ui.screens.mode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rst.player.data.model.PlayerMode
import com.rst.player.ui.theme.BrandAccentGlow
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright

data class ModeVisuals(
    val icon: ImageVector,
    val accent: Color,
    val accentBright: Color,
    val glow: Color
)

fun modeIcon(key: String): ImageVector = when (key) {
    "car" -> Icons.Rounded.DirectionsCar
    "gym" -> Icons.Rounded.FitnessCenter
    "sleep" -> Icons.Rounded.Bedtime
    "star" -> Icons.Rounded.Star
    "bolt" -> Icons.Rounded.Bolt
    "heart" -> Icons.Rounded.Favorite
    "fire" -> Icons.Rounded.LocalFireDepartment
    "run" -> Icons.Rounded.DirectionsRun
    "zen" -> Icons.Rounded.SelfImprovement
    "bulb" -> Icons.Rounded.EmojiObjects
    "pool" -> Icons.Rounded.Pool
    else -> Icons.Rounded.MusicNote
}

fun modeAccent(iconKey: String): Pair<Color, Color> = when (iconKey) {
    "gym" -> Color(0xFFFF5E2B) to Color(0xFFFFA93C)
    "sleep" -> Color(0xFF5B6CFF) to Color(0xFFA79CFF)
    "star" -> Color(0xFFFFC93C) to Color(0xFFFFE082)
    "bolt" -> Color(0xFF00BCD4) to Color(0xFF80F5FF)
    "heart" -> Color(0xFFFF4D6D) to Color(0xFFFF8FA3)
    "fire" -> Color(0xFFFF6D00) to Color(0xFFFFB300)
    "run" -> Color(0xFF26C6DA) to Color(0xFF7DF3FF)
    "zen" -> Color(0xFFAB7CFF) to Color(0xFFD6BFFF)
    "bulb" -> Color(0xFFFFB300) to Color(0xFFFFE082)
    "pool" -> Color(0xFF29B6F6) to Color(0xFF9BE1FF)
    else -> BrandAccent to BrandAccentBright
}

fun visualsFor(mode: PlayerMode): ModeVisuals {
    val (accent, bright) = modeAccent(mode.iconKey)
    val glow = if (mode.iconKey == "car") BrandAccentGlow else bright
    return ModeVisuals(modeIcon(mode.iconKey), accent, bright, glow)
}

fun smartSubtitleFor(mode: PlayerMode): String = when (mode.id) {
    "car" -> "Most played first · then the rest"
    "gym" -> "High-energy favorites first"
    "sleep" -> "Calm songs, gentle order"
    else -> "Your mix, your rules"
}

fun introSubtitleFor(mode: PlayerMode): String = when (mode.id) {
    "car" -> "Drive safe · enjoy the ride"
    "gym" -> "Push hard · feel the burn"
    "sleep" -> "Rest easy · drift away"
    else -> "Your music, your rules"
}

fun modeDisplayTitle(mode: PlayerMode): String =
    if (mode.isBuiltin) "${mode.name.uppercase()} MODE" else mode.name.uppercase()

/**
 * Bespoke "modes" glyph: three rounded mood bars of different heights.
 * Reads as a music-mode selector, distinct from the old Speed gauge.
 */
@Composable
fun ModeGlyphIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    val bars = floatArrayOf(0.44f, 0.72f, 0.34f)
    Canvas(modifier = modifier) {
        val gap = size.width * 0.14f
        val barW = (size.width - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { i, fraction ->
            val h = size.height * fraction
            drawRoundRect(
                color = tint,
                topLeft = Offset((barW + gap) * i, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}
