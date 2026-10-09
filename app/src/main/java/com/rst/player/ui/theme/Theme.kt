package com.rst.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rst.player.data.preferences.AppSettings

/** RST Player signature corner language — soft, modern, card-forward. */
val RstShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RstTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val darkTheme = when (settings.themeMode) {
        AppSettings.THEME_LIGHT -> false
        AppSettings.THEME_DARK, AppSettings.THEME_OLED -> true
        else -> isSystemInDarkTheme()
    }
    val oled = settings.themeMode == AppSettings.THEME_OLED

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            if (darkTheme) {
                dynamicDarkColorScheme(LocalContext.current)
            } else {
                dynamicLightColorScheme(LocalContext.current)
            }
        }

        darkTheme -> {
            val accent = BrandAccent
            val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
            val bg = if (oled) BackgroundOled else BackgroundDark
            val surface = if (oled) BackgroundOled else SurfaceDark
            val container = lerp(accent, bg, 0.86f)
            val onContainer = lerp(accent, Color.White, 0.3f)
            darkColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = container,
                onPrimaryContainer = onContainer,
                secondary = accent,
                onSecondary = onAccent,
                secondaryContainer = container,
                onSecondaryContainer = onContainer,
                tertiary = lerp(accent, Color(0xFF22D3EE), 0.60f),
                onTertiary = Color(0xFF06202A),
                background = bg,
                onBackground = Color(0xFFF5F5F5),
                surface = surface,
                onSurface = Color(0xFFF5F5F5),
                surfaceVariant = if (oled) Color(0xFF101010) else SurfaceDarkHigh,
                onSurfaceVariant = Color(0xFFB8B8B8),
                surfaceContainerLowest = bg,
                surfaceContainerLow = if (oled) Color(0xFF0B0B0B) else Color(0xFF1C1C1C),
                surfaceContainer = if (oled) Color(0xFF101010) else Color(0xFF202020),
                surfaceContainerHigh = if (oled) Color(0xFF161616) else Color(0xFF262626),
                surfaceContainerHighest = if (oled) Color(0xFF1C1C1C) else Color(0xFF2C2C2C),
                outline = Color(0xFF3C3C3C),
                outlineVariant = Color(0xFF2B2B2B),
                error = Color(0xFFCF6679),
                onError = Color.Black
            )
        }

        else -> {
            val accent = BrandAccent
            val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White
            val container = lerp(accent, Color.White, 0.85f)
            lightColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = container,
                onPrimaryContainer = lerp(accent, Color(0xFF141414), 0.15f),
                secondary = accent,
                onSecondary = onAccent,
                secondaryContainer = container,
                onSecondaryContainer = lerp(accent, Color(0xFF141414), 0.15f),
                tertiary = lerp(accent, Color(0xFF0891B2), 0.40f),
                onTertiary = Color.White,
                background = SurfaceLight,
                onBackground = Color(0xFF181818),
                surface = Color.White,
                onSurface = Color(0xFF181818),
                surfaceVariant = Color(0xFFE8E8E8),
                onSurfaceVariant = Color(0xFF565656),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF2F2F2),
                surfaceContainer = Color(0xFFEDEDED),
                surfaceContainerHigh = Color(0xFFE7E7E7),
                surfaceContainerHighest = Color(0xFFE0E0E0),
                outline = Color(0xFFC9C9C9),
                outlineVariant = Color(0xFFE0E0E0),
                error = Color(0xFFB3261E),
                onError = Color.White
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RstShapes,
        content = content
    )
}
