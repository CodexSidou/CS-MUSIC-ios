package com.rst.player.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached audio-mood analysis for a song, computed on-device from the actual
 * music. The deeper feature set lets the engine distinguish a bright pop
 * anthem from a dark chill track with far more nuance.
 */
@Entity(tableName = "song_moods")
data class SongMoodEntity(
    @PrimaryKey val songId: String,
    val bpm: Float,
    val energy: Float,
    val brightness: Float,
    val bassiness: Float,
    val darkness: Float,
    val spectralFlux: Float = 0f,
    val rhythmicDensity: Float = 0f,
    val dynamicRange: Float = 0f,
    val valence: Float = 0.5f
)
