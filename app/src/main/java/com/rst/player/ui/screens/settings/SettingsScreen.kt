package com.rst.player.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rst.player.data.preferences.AppSettings
import com.rst.player.ui.components.backgrounds.AnimatedAppBackground
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.theme.BrandAccent
import com.rst.player.ui.theme.BrandAccentBright
import com.rst.player.ui.viewmodel.SettingsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val graph = LocalGraph.current
    val vm: SettingsViewModel = viewModel {
        SettingsViewModel(graph.userPreferences, graph.youTubeRepository)
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engineVersion by vm.engineVersion.collectAsStateWithLifecycle()
    val engineUpdating by vm.engineUpdating.collectAsStateWithLifecycle()
    val engineReady by vm.engineReady.collectAsStateWithLifecycle()
    val engineError by vm.engineError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.ensureEngineInit() }

    val appContext = LocalContext.current
    val appVersion = remember(appContext) {
        try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }
    val appVersionCode = remember(appContext) {
        try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionCode.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    val history by graph.historyRepository.observeRecent(10000)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val librarySongs by graph.musicRepository.songs.collectAsStateWithLifecycle(initialValue = emptyList())

    val totalPlays = history.sumOf { it.count }
    val topSongs = history.sortedByDescending { it.count }.take(3)
    val topArtists = history.groupBy { it.artist }
        .mapValues { (_, list) -> list.sumOf { it.count } }
        .toList()
        .sortedByDescending { it.second }
        .take(3)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { Header("Your stats") }
        item {
            StatsCard(
                listenMs = settings.totalListenMs,
                plays = totalPlays,
                songs = librarySongs.size,
                topSongs = topSongs,
                topArtists = topArtists
            )
        }

        item { Header("Appearance") }
        item {
            SettingCard {
                SectionLabel("Theme")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        "System" to AppSettings.THEME_SYSTEM,
                        "Light" to AppSettings.THEME_LIGHT,
                        "Dark" to AppSettings.THEME_DARK,
                        "OLED" to AppSettings.THEME_OLED
                    )
                    options.forEachIndexed { index, (label, mode) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size)
                        ) {
                            Text(label)
                        }
                    }
                }
                Spacer(modifier = Modifier.padding(top = 16.dp))
                ToggleRow(
                    title = "Dynamic color (Material You)",
                    subtitle = "Auto colors from your wallpaper",
                    checked = settings.dynamicColor,
                    onToggle = { vm.setDynamicColor(!settings.dynamicColor) }
                )
                ToggleRow(
                    title = "Vibrant Now Playing backdrop",
                    subtitle = "Tint the player with your album art",
                    checked = settings.vibrantBackdrop,
                    onToggle = { vm.setVibrantBackdrop(!settings.vibrantBackdrop) }
                )
            }
        }

        item { Header("Backgrounds") }
        item {
            SettingCard {
                SectionLabel("Animated background")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(backgroundOptions) { option ->
                        BackgroundOptionCard(
                            name = option.label,
                            style = option.style,
                            selected = settings.backgroundStyle == option.style,
                            onClick = { vm.setBackgroundStyle(option.style) }
                        )
                    }
                }
                Text(
                    text = "Live motion backdrops that breathe behind Home, Library, Explore and Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        item { Header("Playback") }
        item {
            SettingCard {
                ToggleRow(
                    title = "Smart auto-play",
                    subtitle = "Keep the music going with smart recommendations",
                    checked = settings.autoplay,
                    onToggle = { vm.setAutoplay(!settings.autoplay) }
                )
                ToggleRow(
                    title = "Pause when headphones unplug",
                    subtitle = "Stops playback when the jack is disconnected",
                    checked = settings.headphonePause,
                    onToggle = { vm.setHeadphonePause(!settings.headphonePause) }
                )
            }
        }

        item { Header("Start screen") }
        item {
            SettingCard {
                SectionLabel("Open on app launch")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        "Home" to AppSettings.START_HOME,
                        "Library" to AppSettings.START_LIBRARY
                    )
                    options.forEachIndexed { index, (label, mode) ->
                        SegmentedButton(
                            selected = settings.startScreen == mode,
                            onClick = { vm.setStartScreen(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        item { Header("Library") }
        item {
            SettingCard {
                ToggleRow(
                    title = "Artists as grid",
                    subtitle = "Show artists in a grid instead of a list",
                    checked = settings.artistGridView,
                    onToggle = { vm.setArtistGrid(!settings.artistGridView) }
                )
            }
        }

        item { Header("YouTube engine") }
        item {
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.OndemandVideo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (engineVersion != null) "yt-dlp $engineVersion" else "yt-dlp bundled",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = when {
                                engineUpdating -> "Updating…"
                                engineReady && engineVersion != null -> "Engine ready — search & downloads run on your device"
                                engineError != null -> engineError!!
                                else -> "Engine initializing…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (engineError != null && engineVersion == null)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (engineUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { vm.updateYoutubeEngine() }) {
                            Icon(Icons.Rounded.Update, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update")
                        }
                    }
                }
            }
        }

        item { Header("About & Legal") }
        item { AboutCard(appVersion = appVersion, versionCode = appVersionCode) }
        item { LegalCard(appVersion = appVersion, versionCode = appVersionCode) }

        item { Header("Diagnostics") }
        item { CrashReportCard() }
    }
}

// ── About & Legal ─────────────────────────────────────────────────────────

private const val DEVELOPER_HANDLE = "rkkizar777-design"
private const val DEVELOPER_URL = "https://github.com/rkkizar777-design"

private const val LEGAL_COPYRIGHT =
    "Copyright \u00A9 2026 rkkizar777-design. All rights reserved. " +
        "RST Player and its source code are the intellectual property of the developer, " +
        "protected under copyright law. Unauthorized copying, reproduction, distribution " +
        "or modification of this application, in whole or in part, is strictly prohibited."

private const val TERMS_OF_USE_TEXT =
    """Last updated: 2026

1. ACCEPTANCE
By downloading, installing or using RST Player ("the App"), you agree to these Terms of Use. If you do not agree, do not use the App.

2. LICENSE TO USE
The developer grants you a limited, non-exclusive, non-transferable, revocable license to install and use the App for personal, non-commercial purposes on devices you own or control.

3. RESTRICTIONS
You may not, without prior written permission:
• Copy, modify, reverse-engineer or create derivative works of the App.
• Distribute, sublicense or sell the App in any form.
• Remove or alter any copyright or proprietary notices.
• Use the App for any unlawful or unauthorized purpose.

4. DISCLAIMER OF WARRANTY
The App is provided "as is" and "as available" without warranties of any kind, whether express or implied, including merchantability, fitness for a particular purpose and non-infringement.

5. LIMITATION OF LIABILITY
To the maximum extent permitted by law, the developer shall not be liable for any indirect, incidental, special, consequential or punitive damages, or any loss of data, arising from the use of the App.

6. CHANGES TO THESE TERMS
These Terms may be updated from time to time. Continued use of the App after changes take effect constitutes acceptance of the revised Terms.

7. CONTACT
Questions? Reach the developer on GitHub: github.com/rkkizar777-design"""

private const val PRIVACY_POLICY_TEXT =
    """Last updated: 2026

Your privacy matters. RST Player is designed to be fully private by default.

1. WHAT WE COLLECT
Nothing. RST Player does not collect, store or transmit any personal data. There is no account, no registration and no tracking.

2. ON-DEVICE PROCESSING
All of your music, listening history and audio analysis stays on your device. Nothing leaves your phone. Smart recommendations and play modes are computed locally.

3. PERMISSIONS
The App may request access to your music library and, optionally, notification access to show playback controls. Downloaded content is stored only on your device.

4. NETWORK ACTIVITY
The only network activity is initiated by you — for example, searching YouTube or downloading a song. Those requests go directly to the relevant service; RST Player does not log or retain them beyond what is needed to play your content.

5. DATA SECURITY
Because no data leaves your device, there is nothing to intercept. Your listening habits remain yours.

6. CHANGES TO THIS POLICY
If this policy changes, the updated version will appear here.

7. CONTACT
Questions? Reach the developer on GitHub: github.com/rkkizar777-design"""

private const val LICENSE_TEXT =
    """RST Player
Copyright \u00A9 2026 rkkizar777-design. All Rights Reserved.

RST Player is proprietary, licensed software. It is not open source and is not free for redistribution.

• You may use the App for personal, non-commercial purposes.
• You may not sell, redistribute, bundle or republish the App, in whole or in part.
• You may not modify, decompile or reverse-engineer the App.
• All copyright, trademark and other intellectual property rights remain the exclusive property of the developer.

OPEN-SOURCE COMPONENTS
RST Player uses open-source libraries under their own licenses, including:
• yt-dlp / youtube-dl (Unlicense) — the download engine
• Jetpack Compose, AndroidX and Material 3 (Apache License 2.0)
• Media3 / ExoPlayer (Apache License 2.0)
• Coil (Apache License 2.0)
• Room and DataStore (Apache License 2.0)

Nothing in the licenses of these components grants you any rights to RST Player itself.

Copyright and all rights reserved."""

@Composable
private fun AboutCard(appVersion: String, versionCode: Long) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BrandAccent.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(listOf(BrandAccent, Color(0xFF3730A3))),
                            CircleShape
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "K",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RST PLAYER",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Fully offline \u00B7 smart \u00B7 private",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )

            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel("Developer")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .border(1.dp, BrandAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RK",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandAccentBright
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DEVELOPER_HANDLE,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Creator \u00B7 Designer \u00B7 Developer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel("Copyright")
            Text(
                text = LEGAL_COPYRIGHT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, BrandAccent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .clickable { uriHandler.openUri(DEVELOPER_URL) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GitHub",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = DEVELOPER_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("github", DEVELOPER_URL))
                }) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy GitHub link",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = "Open GitHub",
                    tint = BrandAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LegalCard(appVersion: String, versionCode: Long) {
    var open by remember { mutableStateOf<String?>(null) }

    SettingCard {
        LegalRow(
            icon = Icons.Rounded.Info,
            title = "Version",
            subtitle = "v$appVersion \u00B7 build $versionCode",
            onClick = null
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        )
        LegalRow(
            icon = Icons.Rounded.Gavel,
            title = "Terms of Use",
            subtitle = "The legal agreement for using RST Player",
            onClick = { open = "terms" }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        )
        LegalRow(
            icon = Icons.Rounded.PrivacyTip,
            title = "Privacy Policy",
            subtitle = "Everything stays on your device",
            onClick = { open = "privacy" }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        )
        LegalRow(
            icon = Icons.Rounded.Copyright,
            title = "License",
            subtitle = "Copyright \u00B7 all rights reserved",
            onClick = { open = "license" }
        )
    }

    when (open) {
        "terms" -> LegalDialog(
            title = "Terms of Use",
            body = TERMS_OF_USE_TEXT,
            onDismiss = { open = null }
        )
        "privacy" -> LegalDialog(
            title = "Privacy Policy",
            body = PRIVACY_POLICY_TEXT,
            onDismiss = { open = null }
        )
        "license" -> LegalDialog(
            title = "License",
            body = LICENSE_TEXT,
            onDismiss = { open = null }
        )
    }
}

@Composable
private fun LegalRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LegalDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF0E1F17),
        title = {
            Column {
                Text(
                    text = "RST PLAYER",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 19.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BrandAccentBright, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun CrashReportCard() {
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var crashCount by remember { mutableIntStateOf(0) }
    var lastCrashKind by remember { mutableStateOf<String?>(null) }
    var deviceInfo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        crashLog = withContext(Dispatchers.IO) {
            runCatching {
                File(context.filesDir, "crash.log").takeIf { it.exists() }?.readText()
            }.getOrNull()
        }
        crashCount = crashLog?.lines()?.count { it.startsWith("=== CRASH REPORT ===") } ?: 0
        lastCrashKind = crashLog?.lines()?.firstOrNull { it.startsWith("Kind") }?.substringAfter(": ")?.trim()
        deviceInfo = crashLog?.lines()?.filter {
            it.startsWith("Device") || it.startsWith("Android") || it.startsWith("CPU") ||
                it.startsWith("Cores") || it.startsWith("Total RAM") || it.startsWith("Heap")
        }?.joinToString(" | ")?.trim()
        loading = false
    }

    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.BugReport,
                contentDescription = null,
                tint = if (crashCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Crash reports",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when {
                        loading -> "Reading log…"
                        crashCount == 0 -> "No crashes logged"
                        else -> "$crashCount crash${if (crashCount > 1) "es" else ""}" +
                            (lastCrashKind?.let { " · last: $it" } ?: "")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (crashCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (deviceInfo != null) {
            Spacer(modifier = Modifier.padding(top = 8.dp))
            Text(
                text = deviceInfo.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!crashLog.isNullOrBlank()) {
            Spacer(modifier = Modifier.padding(top = 10.dp))
            Text(
                text = crashLog.orEmpty().lines().filter { it.isNotBlank() }.take(30).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 14,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.padding(top = 10.dp))
            Row {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash.log", crashLog))
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { File(context.filesDir, "crash.log").delete() }
                        }
                        crashLog = null
                        crashCount = 0
                        lastCrashKind = null
                        deviceInfo = null
                    }
                }) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    listenMs: Long,
    plays: Int,
    songs: Int,
    topSongs: List<com.rst.player.data.db.entity.PlayHistoryEntity>,
    topArtists: List<Pair<String, Int>>
) {
    val topSong = topSongs.firstOrNull()
    val topArtist = topArtists.firstOrNull()

    // Animated counters
    val animPlays = remember { Animatable(0f) }
    val animSongs = remember { Animatable(0f) }
    val animTopPlays = remember { Animatable(0f) }
    val animArtistPlays = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { animPlays.animateTo(plays.toFloat(), spring(stiffness = Spring.StiffnessLow)) }
        launch { animSongs.animateTo(songs.toFloat(), spring(stiffness = Spring.StiffnessLow)) }
        launch { animTopPlays.animateTo((topSong?.count ?: 0).toFloat(), spring(stiffness = Spring.StiffnessLow)) }
        launch { animArtistPlays.animateTo((topArtist?.second ?: 0).toFloat(), spring(stiffness = Spring.StiffnessLow)) }
    }

    // Icon breathing glow
    val infiniteTransition = rememberInfiniteTransition(label = "statsGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1600), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse),
        label = "iconGlow"
    )

    // Staggered entry
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entryAlpha = animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(500),
        label = "entryAlpha"
    )
    val entryOffsetY = animateFloatAsState(
        targetValue = if (entered) 0f else 20f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "entryOffset"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BrandAccent.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                alpha = entryAlpha.value
                translationY = entryOffsetY.value
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    BrandAccent.copy(alpha = glowAlpha + 0.2f),
                                    BrandAccent.copy(alpha = glowAlpha)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF0A0A1F),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MY STATE",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandAccent
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatListenTime(listenMs),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "listening time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated listening time bar
            val maxMs = (listenMs.coerceAtLeast(1))
            val barProgress = animateFloatAsState(
                targetValue = (listenMs.toFloat() / (maxMs * 1.5f)).coerceIn(0f, 1f),
                animationSpec = tween(800, delayMillis = 300),
                label = "bar"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress.value)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(BrandAccent, BrandAccentBright)))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                AnimatedStatCell(
                    value = animPlays.value.toInt().toString(),
                    label = "plays",
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCell(
                    value = animSongs.value.toInt().toString(),
                    label = "songs",
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCell(
                    value = animTopPlays.value.toInt().toString(),
                    label = "top plays",
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCell(
                    value = animArtistPlays.value.toInt().toString(),
                    label = "artist plays",
                    modifier = Modifier.weight(1f)
                )
            }
            if (topSong != null || topArtist != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (topSong != null) {
                        CompactLeader(
                            label = "Top song",
                            value = topSong.title,
                            count = "${topSong.count}x",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (topArtist != null) {
                        CompactLeader(
                            label = "Top artist",
                            value = topArtist.first,
                            count = "${topArtist.second}x",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedStatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactLeader(
    label: String,
    value: String,
    count: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = BrandAccent
        )
    }
}

private fun formatListenTime(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun Header(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

private data class BackgroundOption(val label: String, val style: Int)

private val backgroundOptions = listOf(
    BackgroundOption("None", AppSettings.BACKGROUND_NONE),
    BackgroundOption("Aurora", AppSettings.BACKGROUND_AURORA),
    BackgroundOption("Waves", AppSettings.BACKGROUND_WAVES),
    BackgroundOption("Particles", AppSettings.BACKGROUND_PARTICLES),
    BackgroundOption("Pulse", AppSettings.BACKGROUND_PULSE)
)

@Composable
private fun BackgroundOptionCard(name: String, style: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) BrandAccentBright else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B1010))
        ) {
            AnimatedAppBackground(style = style, modifier = Modifier.fillMaxSize())
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = BrandAccentBright,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) BrandAccentBright else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}
