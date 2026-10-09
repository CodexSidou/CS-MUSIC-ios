package com.rst.player.recommender

import com.rst.player.data.db.entity.SongMoodEntity

/** Coarse mood a track's audio fingerprints point to. */
enum class MoodTag(val label: String) {
    NIGHT("Night"),
    UPBEAT("Upbeat"),
    BRIGHT("Bright"),
    CHILL("Chill"),
    MELLOW("Mellow")
}

object MoodClassifier {
    fun classify(m: SongMoodEntity): MoodTag = when {
        // Night: dark, slow, low energy, low valence
        m.darkness >= 0.62f && m.bpm <= 100f && m.valence < 0.4f -> MoodTag.NIGHT
        // Upbeat: fast, high energy, high rhythmic density
        m.bpm >= 118f && m.energy >= 0.55f && m.rhythmicDensity >= 0.4f -> MoodTag.UPBEAT
        // Bright: high brightness, mid-high energy, positive valence
        m.brightness >= 0.42f && m.energy >= 0.4f && m.valence >= 0.45f -> MoodTag.BRIGHT
        // Chill: low energy, low brightness, low spectral flux
        m.energy <= 0.4f && m.brightness <= 0.45f && m.spectralFlux <= 0.4f -> MoodTag.CHILL
        else -> MoodTag.MELLOW
    }

    /** How well a track's audio profile fits a time-of-day mix, 0..1. */
    fun fitFor(period: TimePeriod, m: SongMoodEntity): Double {
        val bpmNorm = (m.bpm.coerceIn(50f, 180f) - 50f) / 130f
        val midBpm = 1f - kotlin.math.abs(bpmNorm - 0.4f) / 0.4f
        return when (period) {
            TimePeriod.MORNING ->
                ((m.brightness * 2.0f + m.valence + midBpm.coerceAtLeast(0f)) / 4.0f).toDouble()
            TimePeriod.AFTERNOON ->
                ((m.energy + m.bpm.coerceAtMost(170f) / 170f + m.rhythmicDensity * 0.5f) / 2.5f).toDouble()
            TimePeriod.EVENING ->
                (((1.0f - m.energy) + (1.0f - m.darkness) + m.valence * 0.5f) / 2.5f).toDouble()
            TimePeriod.NIGHT ->
                ((m.darkness * 2.0f + (1.0f - m.energy) + (1.0f - bpmNorm) + (1.0f - m.valence)) / 5.0f).toDouble()
        }
    }
}
