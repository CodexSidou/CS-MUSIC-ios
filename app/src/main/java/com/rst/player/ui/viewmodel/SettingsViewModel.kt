package com.rst.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rst.player.data.preferences.AppSettings
import com.rst.player.data.preferences.UserPreferences
import com.rst.player.youtube.YouTubeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val youTubeRepository: YouTubeRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        userPreferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val engineVersion: StateFlow<String?> = youTubeRepository.engineVersion
    val engineUpdating: StateFlow<Boolean> = youTubeRepository.updating
    val engineReady: StateFlow<Boolean> = youTubeRepository.ready
    val engineError: StateFlow<String?> = youTubeRepository.engineInitError

    fun ensureEngineInit() = youTubeRepository.ensureEngineInit()

    fun setThemeMode(mode: Int) = launch { userPreferences.setThemeMode(mode) }
    fun setDynamicColor(b: Boolean) = launch { userPreferences.setDynamicColor(b) }
    fun setAutoplay(b: Boolean) = launch { userPreferences.setAutoplay(b) }
    fun setArtistGrid(b: Boolean) = launch { userPreferences.setArtistGrid(b) }
    fun setSleepMinutes(min: Int) = launch { userPreferences.setSleepMinutes(min) }
    fun setStartScreen(screen: Int) = launch { userPreferences.setStartScreen(screen) }
    fun setHeadphonePause(b: Boolean) = launch { userPreferences.setHeadphonePause(b) }
    fun setVibrantBackdrop(b: Boolean) = launch { userPreferences.setVibrantBackdrop(b) }
    fun setBackgroundStyle(style: Int) = launch { userPreferences.setBackgroundStyle(style) }
    fun updateYoutubeEngine() = youTubeRepository.updateEngine()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
