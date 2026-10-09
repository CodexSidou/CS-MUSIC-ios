package com.rst.player.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rst.player.player.DownloadService
import com.rst.player.ui.theme.BrandAccent

class ShareDownloadActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("share", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val youtubeUrl = extractYouTubeUrl(sharedText)
        val playlistUrl = if (youtubeUrl == null) extractPlaylistUrl(sharedText) else null
        val spotifyUri = extractSpotifyUri(sharedText)
        if (youtubeUrl == null && playlistUrl == null && spotifyUri == null) {
            finish()
            return
        }
        val mode = when {
            spotifyUri != null -> Mode.SPOTIFY_TRACK
            playlistUrl != null -> Mode.YOUTUBE_PLAYLIST
            else -> Mode.YOUTUBE_VIDEO
        }
        val subtitle = when (mode) {
            Mode.SPOTIFY_TRACK -> "Finds this song on YouTube and saves it with its Spotify name and artist"
            Mode.YOUTUBE_PLAYLIST -> "Downloads every track in this playlist/album into your library"
            Mode.YOUTUBE_VIDEO -> "Saved to your library with its name, artist and cover"
        }
        setContent {
            QualityPickerCard(
                initial = prefs.getInt(KEY_QUALITY, 0),
                subtitle = subtitle,
                onCancel = { finish() },
                onDownload = { quality ->
                    prefs.edit().putInt(KEY_QUALITY, quality).apply()
                    when (mode) {
                        Mode.SPOTIFY_TRACK -> DownloadService.startSpotify(this, spotifyUri!!, quality)
                        Mode.YOUTUBE_PLAYLIST -> DownloadService.startPlaylist(this, playlistUrl!!, quality)
                        Mode.YOUTUBE_VIDEO -> DownloadService.start(this, youtubeUrl!!, "YouTube audio", quality)
                    }
                    finish()
                }
            )
        }
    }

    private fun extractPlaylistUrl(text: String): String? {
        val pattern = Regex(
            """(?i)https?://[^\s"<>]*?(?:youtube\.com|music\.youtube\.com)/playlist\?[^\s"<>]*"""
        )
        val url = pattern.find(text)?.value ?: return null
        return if (extractPlaylistId(url) != null) url else null
    }

    private fun extractPlaylistId(url: String): String? =
        Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(url)?.groupValues?.get(1)

    private fun extractSpotifyUri(text: String): String? {
        Regex("""(?i)spotify:track:[a-zA-Z0-9]{22}""")
            .find(text)?.value?.let { return it }
        val pattern = Regex("""(?i)https?://[^\s"<>]*?open\.spotify\.com/track/[^\s"<>]*""")
        val url = pattern.find(text)?.value ?: return null
        return if (extractSpotifyTrackId(url) != null) url else null
    }

    private fun extractSpotifyTrackId(url: String): String? {
        Regex("""(?i)open\.spotify\.com/track/([a-zA-Z0-9]{22})""")
            .find(url)?.let { return it.groupValues[1] }
        Regex("""(?i)spotify:track:([a-zA-Z0-9]{22})""")
            .find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractYouTubeUrl(text: String): String? {
        val pattern = Regex(
            """(https?://[^\s"<>]*?(?:youtube\.com/(?:watch\?(?:[^#\s]*?&)?v=|shorts/|live/|embed/)|youtu\.be/)[^\s"<>]*)"""
        )
        val url = pattern.find(text)?.value ?: return null
        return if (extractVideoId(url) != null) url else null
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""[?&]v=([a-zA-Z0-9_-]{11})"""),
            Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""youtube\.com/(?:shorts|live|embed)/([a-zA-Z0-9_-]{11})""")
        )
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    companion object {
        private const val KEY_QUALITY = "download_quality"
    }

    private enum class Mode { YOUTUBE_VIDEO, YOUTUBE_PLAYLIST, SPOTIFY_TRACK }
}

private val qualityOptions = listOf(
    Triple(0, "Highest", "Best quality available"),
    Triple(1, "High", "~256 kbps"),
    Triple(2, "Medium", "~192 kbps"),
    Triple(3, "Low", "~128 kbps")
)

@Composable
private fun QualityPickerCard(
    initial: Int,
    subtitle: String,
    onCancel: () -> Unit,
    onDownload: (Int) -> Unit
) {
    var selected by remember { mutableIntStateOf(initial.coerceIn(0, 3)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF16161A),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Audio quality",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                qualityOptions.forEach { (index, label, sub) ->
                    QualityRow(
                        label = label,
                        subtitle = sub,
                        selected = selected == index,
                        onClick = { selected = index }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = { onDownload(selected) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandAccent,
                            contentColor = Color(0xFF062B14)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Download", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) BrandAccent else Color.White.copy(alpha = 0.35f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(BrandAccent)
                )
            }
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) BrandAccent else Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
