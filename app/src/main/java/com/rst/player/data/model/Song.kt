package com.rst.player.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dataPath: String,
    val uri: Uri,
    val year: Int?,
    val trackNumber: Int?
) {
    val isUnknownArtist: Boolean get() = artist == UNKNOWN_ARTIST
    val isUnknownAlbum: Boolean get() = album == UNKNOWN_ALBUM

    companion object {
        const val UNKNOWN_ARTIST = "Unknown Artist"
        const val UNKNOWN_ALBUM = "Unknown Album"
    }
}
