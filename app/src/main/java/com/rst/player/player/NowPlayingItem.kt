package com.rst.player.player

import com.rst.player.data.model.Song

/**
 * Unified "what is playing right now". Local library songs carry an [albumId]
 * (artwork comes from MediaStore); online previews carry an [artworkUrl]
 * instead and are played from the app cache.
 */
data class NowPlayingItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: Long? = null,
    val artworkUrl: String? = null
) {
    val isLocal: Boolean get() = albumId != null

    companion object {
        fun fromSong(song: Song): NowPlayingItem = NowPlayingItem(
            mediaId = song.id.toString(),
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumId = song.albumId
        )
    }
}
