package com.rst.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rst.player.data.repository.MusicRepository
import com.rst.player.data.model.Album
import com.rst.player.data.model.Artist
import com.rst.player.data.model.Song
import com.rst.player.player.PlayerController
import com.rst.player.player.OnlineTrack
import com.rst.player.youtube.YouTubeChannel
import com.rst.player.youtube.YouTubeRepository
import com.rst.player.youtube.YouTubeVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val musicRepository: MusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // In-memory filtering over the whole library is pushed to a background
    // thread so typing never blocks the main/UI thread.
    val songs: StateFlow<List<Song>> = _query
        .flatMapLatest { q -> flowOf(musicRepository.searchSongs(q)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = _query
        .flatMapLatest { q -> flowOf(musicRepository.searchAlbums(q)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = _query
        .flatMapLatest { q -> flowOf(musicRepository.searchArtists(q)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineResults: StateFlow<List<YouTubeVideo>> = youTubeRepository.results
    val onlineAlbums: StateFlow<List<YouTubeVideo>> = youTubeRepository.albumResults
    val onlineArtists: StateFlow<List<YouTubeChannel>> = youTubeRepository.artists
    val onlineSearching: StateFlow<Boolean> = youTubeRepository.searching
    val onlineError: StateFlow<String?> = youTubeRepository.error

    private val _lastOnlineQuery = MutableStateFlow("")
    val lastOnlineQuery: StateFlow<String> = _lastOnlineQuery.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun searchOnline() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        _lastOnlineQuery.value = q
        viewModelScope.launch { youTubeRepository.search(q) }
    }

    fun searchArtist(name: String) {
        _query.value = name
        searchOnline()
    }

    fun clearOnlineError() = youTubeRepository.clearError()

    private val _previewingId = MutableStateFlow<String?>(null)
    val previewingId: StateFlow<String?> = _previewingId.asStateFlow()

    /** Streams a non-downloaded song into the cache and plays it once ready. */
    fun playPreview(video: YouTubeVideo) {
        if (_previewingId.value != null) return
        viewModelScope.launch {
            _previewingId.value = video.id
            val result = youTubeRepository.preview(video)
            _previewingId.value = null
            if (result != null) {
                playerController.playOnline(
                    items = listOf(
                        OnlineTrack(
                            mediaId = result.mediaId,
                            title = result.title,
                            artist = result.artist,
                            uri = result.filePath,
                            artworkUri = result.artworkPath
                        )
                    ),
                    startIndex = 0,
                    shuffle = false
                )
            }
        }
    }

    /** Streams a queue of online songs (used by the online artist profile). */
    fun playPreviewQueue(videos: List<YouTubeVideo>, startIndex: Int, shuffle: Boolean = false) {
        if (videos.isEmpty()) return
        viewModelScope.launch {
            for ((i, video) in videos.withIndex()) {
                _previewingId.value = video.id
                val result = youTubeRepository.preview(video)
                _previewingId.value = null
                if (result != null) {
                    val track = OnlineTrack(
                        mediaId = result.mediaId,
                        title = result.title,
                        artist = result.artist,
                        uri = result.filePath,
                        artworkUri = result.artworkPath
                    )
                    if (i == startIndex) {
                        playerController.playOnline(listOf(track), 0, shuffle)
                    } else {
                        playerController.appendOnline(listOf(track))
                    }
                }
            }
        }
    }
}
