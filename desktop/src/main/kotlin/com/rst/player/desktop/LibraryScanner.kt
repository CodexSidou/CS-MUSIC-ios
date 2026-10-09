package com.rst.player.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File

data class Track(
    val file: File,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

/** Recursive local-music scanner with tag + duration reading via jaudiotagger. */
object LibraryScanner {

    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "mp4", "aac", "wav", "flac", "ogg", "opus", "webm")

    /** Formats JavaFX Media can decode; anything else is listed but not playable. */
    val PLAYABLE_EXTENSIONS = setOf("mp3", "m4a", "mp4", "aac", "wav")

    suspend fun scan(dirs: List<File>): List<Track> = withContext(Dispatchers.IO) {
        val out = ArrayList<Track>(256)
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                .forEach { f -> readTrack(f)?.let(out::add) }
        }
        out.sortBy { it.artist.lowercase() + it.album.lowercase() + it.file.name.lowercase() }
        out
    }

    private fun readTrack(f: File): Track? {
        var title = f.nameWithoutExtension
        var artist = "Unknown artist"
        var album = ""
        var durationMs = 0L
        try {
            val af = AudioFileIO.read(f)
            val header = af.audioHeader
            durationMs = header.trackLength * 1000L
            val tag = af.tag
            if (tag != null) {
                tag.getFirst("TITLE")?.takeIf { it.isNotBlank() }?.let { title = it }
                tag.getFirst("ARTIST")?.takeIf { it.isNotBlank() }?.let { artist = it }
                tag.getFirst("ALBUM")?.takeIf { it.isNotBlank() }?.let { album = it }
            }
        } catch (_: Exception) {
            // Untagged/unreadable file: keep filename-based metadata.
            if (durationMs == 0L && f.length() <= 0) return null
        }
        return Track(f, title, artist, album.ifBlank { "Singles" }, durationMs)
    }

    /** Embedded cover art bytes of a track, or null. */
    fun artworkBytes(t: Track): ByteArray? = try {
        AudioFileIO.read(t.file).tag?.firstArtwork?.binaryData
    } catch (_: Exception) {
        null
    }
}
