package com.rst.player.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.compose.ui.graphics.toComposeImageBitmap
import java.io.File

// ---- RST Player brand palette (mirrors the Android theme) ----
val RstAccent = Color(0xFF6366F1)
val RstAccentBright = Color(0xFF818CF8)
val RstAccentGlow = Color(0xFF4F46E5)
val RstCyan = Color(0xFF22D3EE)
val RstBg = Color(0xFF0B0D14)
val RstSurface = Color(0xFF151824)
val RstSurfaceHigh = Color(0xFF1E2235)
val RstOnBg = Color(0xFFF2F3FA)
val RstOnBgDim = Color(0xFF9BA0B8)

@Composable
fun RstDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = RstAccent,
            onPrimary = Color.White,
            primaryContainer = RstAccentGlow,
            secondary = RstAccentBright,
            tertiary = RstCyan,
            background = RstBg,
            onBackground = RstOnBg,
            surface = RstSurface,
            onSurface = RstOnBg,
            surfaceVariant = RstSurfaceHigh,
            onSurfaceVariant = RstOnBgDim,
            outline = Color(0xFF333850),
            error = Color(0xFFCF6679)
        ),
        content = content
    )
}

@Composable
fun BrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(RstAccent, RstCyan))),
            contentAlignment = Alignment.Center
        ) {
            Text("R", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Column {
            Text(
                "RST PLAYER",
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                letterSpacing = 2.sp,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(color = RstAccentGlow.copy(alpha = 0.9f), blurRadius = 14f)
                )
            )
            Text("for PC", color = RstOnBgDim, fontSize = 11.sp, letterSpacing = 1.sp)
        }
    }
}

/** Rounded pill tab used in the header. */
@Composable
fun PillTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Brush.linearGradient(listOf(RstAccent, RstAccentBright))
    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else RstOnBgDim,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

@Composable
fun PlayGlyphButton(label: String, size: Int, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) Brush.linearGradient(listOf(RstAccent, RstAccentBright)) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) Color.White else RstOnBg,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    duration: String,
    isCurrent: Boolean,
    playable: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onDoubleClick: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val bg = if (isCurrent) Brush.horizontalGradient(
        listOf(RstAccent.copy(alpha = 0.22f), Color.Transparent)
    ) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .combinedClick(onDoubleClick = onDoubleClick, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (isCurrent) RstAccentBright else RstOnBg,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, color = RstOnBgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!playable) Text("format n/a", color = RstOnBgDim.copy(alpha = 0.6f), fontSize = 11.sp)
        Text(duration, color = RstOnBgDim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
        trailing?.invoke(this)
    }
}

/** Double-click aware click modifier (single click optional). */
private fun Modifier.combinedClick(onDoubleClick: () -> Unit, onClick: (() -> Unit)?): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null
        ) {
            val now = System.currentTimeMillis()
            if (now - lastClick < 350) {
                lastClick = 0
                onDoubleClick()
            } else {
                lastClick = now
                onClick?.invoke()
            }
        }
    )

private var lastClick = 0L

@Composable
fun Artwork(bytes: ByteArray?, size: Int) {
    val bitmap: ImageBitmap? = remember(bytes?.size ?: 0, bytes?.contentHashCode() ?: 0) {
        bytes?.let {
            runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(RstSurfaceHigh, RstAccentGlow.copy(alpha = 0.45f)))),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text("♪", color = RstAccentBright, fontSize = (size * 0.4f).sp)
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = RstOnBgDim) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ProgressBar(p: Float) {
    if (p > 0f && p < 1f) {
        LinearProgressIndicator(
            progress = p,
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = RstAccentBright,
            trackColor = RstSurfaceHigh
        )
    } else {
        Spacer(Modifier.height(3.dp))
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
