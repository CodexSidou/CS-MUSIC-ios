package com.rst.player.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val playedAt: Long = System.currentTimeMillis(),
    val count: Int = 1
)
