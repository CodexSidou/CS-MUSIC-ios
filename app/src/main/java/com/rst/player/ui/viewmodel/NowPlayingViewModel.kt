package com.rst.player.ui.viewmodel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.rst.player.data.model.Song
import com.rst.player.data.repository.MusicRepository
import com.rst.player.player.PlayerController
import com.rst.player.player.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NowPlayingViewModel(
    private val playerController: PlayerController,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val playerUi: StateFlow<PlaybackUiState> = playerController.ui

    /**
     * Reactive current song — derived by combining the player state and the song list.
     * This replaces the old currentSong() method that performed a linear scan on every
     * recomposition. Compose will only recompose when the actual Song object changes.
     */
    val currentSong: StateFlow<Song?> = combine(
        playerController.ui,
        musicRepository.songs
    ) { ui, songs ->
        val id = ui.currentMediaId ?: return@combine null
        songs.find { it.id.toString() == id }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val _sleepRemaining = MutableStateFlow(0L)
    val sleepRemaining: StateFlow<Long> = _sleepRemaining.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var sleepEndTime: Long = 0

    fun togglePlay() = playerController.togglePlay()
    fun next() = playerController.next()
    fun previous() = playerController.previous()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun setShuffle(b: Boolean) = playerController.setShuffle(b)
    fun cycleRepeat() {
        val current = playerUi.value.repeatMode
        val next = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        playerController.setRepeatMode(next)
    }

    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        sleepEndTime = System.currentTimeMillis() + minutes * 60_000L
        updateSleepTicker()
    }

    fun cancelSleepTimer() {
        sleepEndTime = 0
        handler.removeCallbacksAndMessages(null)
        _sleepRemaining.value = 0
    }

    private fun updateSleepTicker() {
        if (sleepEndTime <= 0) return
        val remaining = sleepEndTime - System.currentTimeMillis()
        if (remaining <= 0) {
            playerController.stop()
            _sleepRemaining.value = 0
            sleepEndTime = 0
            return
        }
        _sleepRemaining.value = remaining
        handler.postDelayed({ updateSleepTicker() }, 1000)
    }

    fun playQueue(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        playerController.playQueue(songs, startIndex, shuffle)
    }

    override fun onCleared() {
        handler.removeCallbacksAndMessages(null)
    }
}
