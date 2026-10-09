package com.rst.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.rst.player.data.db.entity.SongMoodEntity
import com.rst.player.data.model.Song
import com.rst.player.data.repository.HistoryRepository
import com.rst.player.data.repository.MoodRepository
import com.rst.player.data.repository.MusicRepository
import com.rst.player.recommender.RecommendationEngine
import com.rst.player.recommender.TimePeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val engine: RecommendationEngine,
    private val historyRepository: HistoryRepository,
    private val moodRepository: MoodRepository
) : ViewModel() {

    data class TopArtist(val name: String, val artUri: Uri?)

    data class UiState(
        val scanning: Boolean = false,
        val songCount: Int = 0,
        val madeForYouTitle: String = "",
        val madeForYou: List<Song> = emptyList(),
        val onRepeat: List<Song> = emptyList(),
        val discover: List<Song> = emptyList(),
        val moods: Map<String, SongMoodEntity> = emptyMap(),
        val recentlyPlayed: List<Song> = emptyList(),
        val topArtists: List<TopArtist> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.songs.collect { songs ->
                _state.value = buildState(songs, _state.value.moods)
                analyzeMoods(songs)
            }
        }
    }

    /** Lazily fingerprints a handful of tracks in the background so the mood
     *  mixes improve on every visit without stalling the first frame. */
    private suspend fun analyzeMoods(songs: List<Song>) {
        if (songs.isEmpty()) return
        val candidates = withContext(Dispatchers.Default) {
            val taste = engine.buildTaste()
            songs.sortedByDescending { taste.artistAffinity[it.artist] ?: 0.0 }.take(40)
        }
        val moods = moodRepository.analyzeBatch(candidates, maxNew = 6)
        if (moods.isNotEmpty()) {
            val merged = _state.value.moods + moods
            _state.value = buildState(songs, merged)
        }
    }

    private fun periodTitle(period: TimePeriod): String = when (period) {
        TimePeriod.MORNING -> "Made for this morning"
        TimePeriod.AFTERNOON -> "Afternoon picks"
        TimePeriod.EVENING -> "Evening unwind"
        TimePeriod.NIGHT -> "Night drive"
    }

    private suspend fun buildState(songs: List<Song>, moods: Map<String, SongMoodEntity>): UiState {
        return try {
            withContext(Dispatchers.Default) {
                val recent = historyRepository.recent(15)
                val recentlyPlayed = recent.mapNotNull { h ->
                    songs.find { it.id.toString() == h.songId }
                }

                val played = historyRepository.mostPlayed(60)
                val playedCount = played.associateBy { it.songId }
                val playedSongs = played.mapNotNull { h ->
                    songs.find { it.id.toString() == h.songId }
                }

                // On Repeat: what you play most right now.
                val onRepeat = playedSongs
                    .sortedByDescending { playedCount[it.id.toString()]?.count ?: 0 }
                    .take(10)

                // Discover something new: forgotten + unplayed tracks, mixed across artists.
                val playedIds = played.map { it.songId }.toSet()
                val discover = engine.discover(10, excludeIds = playedIds, moods = moods)

                val period = TimePeriod.now()
                val madeForYou = if (songs.isEmpty()) emptyList()
                else engine.madeForYou(12, period = period, moods = moods)

                UiState(
                    scanning = musicRepository.scanning.value,
                    songCount = songs.size,
                    madeForYouTitle = periodTitle(period),
                    madeForYou = madeForYou,
                    onRepeat = onRepeat,
                    discover = discover,
                    moods = moods,
                    recentlyPlayed = recentlyPlayed,
                    topArtists = musicRepository.artists.value
                        .sortedByDescending { it.songCount }
                        .take(10)
                        .map { TopArtist(it.name, musicRepository.artistArtUri(it.name)) }
                )
            }
        } catch (e: Exception) {
            UiState(
                scanning = musicRepository.scanning.value,
                songCount = songs.size,
                moods = moods
            )
        }
    }

    fun rescan() = musicRepository.scan()
}
