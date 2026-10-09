package com.rst.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rst.player.player.OnlineTrack
import com.rst.player.player.PlayerController
import com.rst.player.youtube.YouTubeRepository
import com.rst.player.youtube.YouTubeRepository.OnlineArtistInfo
import com.rst.player.youtube.YouTubeVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnlineArtistViewModel(
    private val artistId: Long,
    private val artistName: String,
    private val youTubeRepository: YouTubeRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _profile = MutableStateFlow<OnlineArtistInfo?>(null)
    val profile: StateFlow<OnlineArtistInfo?> = _profile.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _previewingId = MutableStateFlow<String?>(null)
    val previewingId: StateFlow<String?> = _previewingId.asStateFlow()

    init {
        viewModelScope.launch {
            _loading.value = true
            val p = if (artistId > 0) {
                youTubeRepository.artistProfile(artistId)
            } else {
                youTubeRepository.artistProfileByName(artistName)
            }
            _profile.value = p
            _loading.value = false
            if (p == null) _error.value = "Couldn't load this artist"
        }
    }

    fun playPreview(video: YouTubeVideo) {
        if (_previewingId.value != null) return
        viewModelScope.launch {
            _previewingId.value = video.id
            val result = youTubeRepository.preview(video)
            _previewingId.value = null
            if (result != null) {
                playerController.playOnline(listOf(result.toTrack()), 0, false)
            }
        }
    }

    fun playAll(shuffle: Boolean) {
        val songs = _profile.value?.topSongs ?: return
        playQueue(songs, 0, shuffle)
    }

    fun playQueue(videos: List<YouTubeVideo>, startIndex: Int, shuffle: Boolean = false) {
        if (videos.isEmpty()) return
        viewModelScope.launch {
            for ((i, video) in videos.withIndex()) {
                _previewingId.value = video.id
                val result = youTubeRepository.preview(video)
                _previewingId.value = null
                if (result != null) {
                    if (i == startIndex) {
                        playerController.playOnline(listOf(result.toTrack()), 0, shuffle)
                    } else {
                        playerController.appendOnline(listOf(result.toTrack()))
                    }
                }
            }
        }
    }

    fun download(video: YouTubeVideo) = youTubeRepository.runDownload(video)

    fun downloadAll(videos: List<YouTubeVideo>) = youTubeRepository.runDownloads(videos)

    private fun com.rst.player.youtube.YouTubeRepository.PreviewResult.toTrack() = OnlineTrack(
        mediaId = mediaId,
        title = title,
        artist = artist,
        uri = filePath,
        artworkUri = artworkPath
    )
}
