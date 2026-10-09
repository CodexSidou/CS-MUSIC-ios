package com.rst.player.data.db.entity

import android.net.Uri
import com.rst.player.data.model.Song

fun PlaylistSongEntity.toSong(): Song = Song(
    id = songId.toLongOrNull() ?: -1L,
    title = title,
    artist = artist,
    album = album,
    albumId = albumArtUri.toLongOrNull() ?: 0L,
    durationMs = durationMs,
    dataPath = "",
    uri = Uri.parse(uri),
    year = null,
    trackNumber = null
)
