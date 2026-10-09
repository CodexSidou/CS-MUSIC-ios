package com.rst.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rst.player.data.db.entity.PlaylistEntity
import com.rst.player.data.repository.MusicRepository
import com.rst.player.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistEntity>> =
        playlistRepository.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistCounts: StateFlow<Map<Long, Int>> =
        playlistRepository.observePlaylistCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun rescan() = musicRepository.scan()

    fun createPlaylist(name: String, onDone: (Long?) -> Unit = {}) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(name)
            onDone(id)
        }
    }
}
