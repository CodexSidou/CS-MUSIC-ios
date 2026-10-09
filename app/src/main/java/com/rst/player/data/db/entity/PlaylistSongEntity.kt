package com.rst.player.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: String,
    val uri: String,
    val durationMs: Long,
    val addedAt: Long = System.currentTimeMillis(),
    val position: Int = 0
)
