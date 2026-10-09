package com.rst.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rst.player.data.db.entity.SongMoodEntity
import com.rst.player.data.db.entity.toSong
import com.rst.player.data.model.PlayerMode
import com.rst.player.data.model.Song
import com.rst.player.data.repository.HistoryRepository
import com.rst.player.data.repository.ModeRepository
import com.rst.player.data.repository.MoodRepository
import com.rst.player.data.repository.MusicRepository
import com.rst.player.data.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Whether a mode ranks its head queue by audio energy, calmness, or taste. */
private enum class ModeEnergyProfile { HIGH, CALM, NEUTRAL }

private fun energyProfileFor(mode: PlayerMode): ModeEnergyProfile = when (mode.id) {
    "gym" -> ModeEnergyProfile.HIGH
    "sleep" -> ModeEnergyProfile.CALM
    "car" -> ModeEnergyProfile.NEUTRAL
    else -> when (mode.iconKey) {
        "fire", "bolt", "run" -> ModeEnergyProfile.HIGH
        "zen", "pool", "bulb" -> ModeEnergyProfile.CALM
        else -> ModeEnergyProfile.NEUTRAL
    }
}

/**
 * Builds the queue for a play mode. When the mode has a curated playlist
 * it plays those songs in order; otherwise it falls back to the smart
 * mix (latest installed × most played, interleaved). Mood modes rank the
 * head of the queue by on-device audio energy — Gym leads with high-energy
 * tracks, Sleep with calm ones — while Car and neutral custom modes keep
 * a play-count bias.
 */
class ModeViewModel(
    private val modeId: String,
    private val modeRepository: ModeRepository,
    private val musicRepository: MusicRepository,
    private val historyRepository: HistoryRepository,
    private val playlistRepository: PlaylistRepository,
    private val moodRepository: MoodRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val queue: List<Song> = emptyList(),
        val playedCount: Int = 0,
        val totalCount: Int = 0,
        val isManual: Boolean = false,
        val manualEmpty: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            modeRepository.modes.collect { modes ->
                val mode = modes.find { it.id == modeId }
                _state.value = if (mode == null) {
                    UiState(loading = false)
                } else {
                    buildQueue(mode)
                }
            }
        }
    }

    private suspend fun buildQueue(mode: PlayerMode): UiState {
        val playlistId = mode.playlistId
        if (playlistId != null) {
            val entities = playlistRepository.getSongs(playlistId)
            if (entities.isEmpty()) {
                return UiState(loading = false, isManual = true, manualEmpty = true)
            }
            val songs = entities.map { it.toSong() }
                .mapNotNull { s -> musicRepository.findSongByStringId(s.id.toString()) ?: s }
            return UiState(
                loading = false,
                queue = songs,
                totalCount = songs.size,
                isManual = true
            )
        }

        val songs = musicRepository.songs.value
        if (songs.isEmpty()) return UiState(loading = false)

        val byId = songs.associateBy { it.id.toString() }

        val profile = energyProfileFor(mode)
        val moods = if (profile == ModeEnergyProfile.NEUTRAL) emptyMap() else moodRepository.moodsFor(songs)

        // --- Pool 1: most played (recent history weighted by play count) ---
        val played = historyRepository.mostPlayed(200)
            .mapNotNull { byId[it.songId] }

        // --- Pool 2: latest installed songs (by file date, newest first) ---
        val latest = withContext(Dispatchers.Default) {
            songs.sortedByDescending {
                try {
                    File(it.dataPath).lastModified()
                } catch (_: Exception) {
                    0L
                }
            }
        }

        val seen = HashSet<String>()
        val queue = mutableListOf<Song>()

        fun addIfNew(song: Song) {
            if (seen.add(song.id.toString())) queue.add(song)
        }

        // Interleave: newest + most played. Mood modes rank each tier by
        // energy (gym) or calmness (sleep); neutral modes keep a shuffle.
        val tierSize = 40
        val latestTier = rankTier(latest.take(tierSize), moods, profile)
        val latestIds = latestTier.map { it.id.toString() }.toSet()
        val playedTier = rankTier(
            played.filter { it.id.toString() !in latestIds }.take(tierSize),
            moods,
            profile
        )

        var li = 0
        var pi = 0
        // Alternate: 2 newest → 1 most-played → repeat
        while (li < latestTier.size || pi < playedTier.size) {
            if (li < latestTier.size) { addIfNew(latestTier[li]); li++ }
            if (li < latestTier.size) { addIfNew(latestTier[li]); li++ }
            if (pi < playedTier.size) { addIfNew(playedTier[pi]); pi++ }
        }

        // Fill remaining with the rest of the library, de-streaked by artist
        val remaining = songs.filter { it.id.toString() !in seen }
        val groups = remaining
            .groupBy { it.artist }
            .values
            .map { it.shuffled().toMutableList() }
            .shuffled()

        var advanced = true
        while (advanced) {
            advanced = false
            for (group in groups) {
                if (group.isNotEmpty()) {
                    addIfNew(group.removeAt(0))
                    advanced = true
                }
            }
        }

        return UiState(
            loading = false,
            queue = queue,
            playedCount = played.size,
            totalCount = queue.size
        )
    }

    /**
     * Orders a tier so tracks with the mode's desired audio profile lead the
     * queue; tracks without mood data keep a shuffled tail so the queue never
     * repeats in the same order.
     */
    private fun rankTier(
        tier: List<Song>,
        moods: Map<String, SongMoodEntity>,
        profile: ModeEnergyProfile
    ): List<Song> {
        if (profile == ModeEnergyProfile.NEUTRAL) return tier.shuffled()
        val ranked = tier.mapNotNull { song ->
            moodScore(song, moods, profile)?.let { song to it }
        }.sortedByDescending { it.second }.map { it.first }
        val unscored = tier.filter { moods[it.id.toString()] == null }.shuffled()
        return ranked + unscored
    }

    /** 0..1-ish fit of a song's audio fingerprints to the mode's energy. */
    private fun moodScore(
        song: Song,
        moods: Map<String, SongMoodEntity>,
        profile: ModeEnergyProfile
    ): Double? {
        val m = moods[song.id.toString()] ?: return null
        val bpmNorm = (m.bpm.coerceIn(50f, 180f) - 50f) / 130f
        return when (profile) {
            ModeEnergyProfile.HIGH ->
                m.energy * 1.0 + bpmNorm * 0.8 + m.rhythmicDensity * 0.3 + m.valence * 0.2
            ModeEnergyProfile.CALM ->
                (1f - m.energy) * 1.2 + (1f - bpmNorm) * 0.8 + m.darkness * 0.4
            ModeEnergyProfile.NEUTRAL -> 0.0
        }
    }
}
