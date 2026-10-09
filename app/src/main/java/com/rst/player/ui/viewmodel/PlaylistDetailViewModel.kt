package com.rst.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rst.player.data.db.entity.toSong
import com.rst.player.data.repository.MusicRepository
import com.rst.player.data.repository.PlaylistRepository
import com.rst.player.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    val playlistId: Long,
    val isFavorites: Boolean,
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    init {
        viewModelScope.launch {
            if (isFavorites) {
                _name.value = "Favorites"
                playlistRepository.observeFavoritesSongs().collect { entities ->
                    _songs.value = entities.map { it.toSong() }
                }
            } else {
                playlistRepository.getPlaylist(playlistId)?.let { _name.value = it.name }
                playlistRepository.observeSongs(playlistId).collect { entities ->
                    _songs.value = entities.map { it.toSong() }
                }
            }
        }
    }

    fun removeSong(song: Song) {
        viewModelScope.launch {
            if (isFavorites) {
                playlistRepository.toggleFavorite(song)
            } else {
                playlistRepository.removeSong(playlistId, song.id.toString())
            }
        }
    }

    fun deletePlaylist() {
        viewModelScope.launch {
            if (!isFavorites) playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun songsForPlayback(startIndex: Int = 0): List<Song> = _songs.value

    fun resolveForPlayback(): List<Song> {
        // prefer live library copies so metadata is fresh
        return _songs.value.mapNotNull { s -> musicRepository.findSongByStringId(s.id.toString()) ?: s }
    }
}
