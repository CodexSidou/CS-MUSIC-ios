package com.rst.player.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.rst.player.data.mediastore.MediaStoreScanner
import com.rst.player.data.model.Song

fun OnlineTrack.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build()
        )
    // The cover is a cached file on disk — Media3/Coil only load it when the
    // URI has a scheme, so a bare filesystem path must become a file:// URI.
    val art = artworkUri?.takeIf { it.isNotBlank() }?.let { raw ->
        if (raw.startsWith("http://") || raw.startsWith("https://") ||
            raw.startsWith("file://") || raw.startsWith("content://")
        ) {
            Uri.parse(raw)
        } else {
            Uri.fromFile(java.io.File(raw))
        }
    }
    if (art != null) {
        builder.setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(art)
                .build()
        )
    }
    return builder.build()
}

fun Song.toMediaItem(): MediaItem {
    val art = MediaStoreScanner.albumArtUri(albumId) ?: return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(art)
                .build()
        )
        .build()
}
