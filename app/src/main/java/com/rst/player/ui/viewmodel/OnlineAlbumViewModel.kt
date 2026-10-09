package com.rst.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rst.player.player.OnlineTrack
import com.rst.player.player.PlayerController
import com.rst.player.youtube.YouTubeRepository
import com.rst.player.youtube.YouTubeRepository.OnlineAlbumInfo
import com.rst.player.youtube.YouTubeVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnlineAlbumViewModel(
    private val collectionId: Long,
    private val youTubeRepository: YouTubeRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _album = MutableStateFlow<OnlineAlbumInfo?>(null)
    val album: StateFlow<OnlineAlbumInfo?> = _album.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _previewingId = MutableStateFlow<String?>(null)
    val previewingId: StateFlow<String?> = _previewingId.asStateFlow()

    init {
        viewModelScope.launch {
            _loading.value = true
            val a = youTubeRepository.albumInfo(collectionId)
            _album.value = a
            _loading.value = false
            if (a == null) _error.value = "Couldn't load this album"
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
        val tracks = _album.value?.tracks ?: return
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            for ((i, video) in tracks.withIndex()) {
                _previewingId.value = video.id
                val result = youTubeRepository.preview(video)
                _previewingId.value = null
                if (result != null) {
                    if (i == 0) {
                        playerController.playOnline(listOf(result.toTrack()), 0, shuffle)
                    } else {
                        playerController.appendOnline(listOf(result.toTrack()))
                    }
                }
            }
        }
    }

    fun download(video: YouTubeVideo) = youTubeRepository.runDownload(video)

    fun downloadAll() {
        val tracks = _album.value?.tracks ?: return
        if (tracks.isNotEmpty()) youTubeRepository.runDownloads(tracks)
    }

    private fun com.rst.player.youtube.YouTubeRepository.PreviewResult.toTrack() = OnlineTrack(
        mediaId = mediaId,
        title = title,
        artist = artist,
        uri = filePath,
        artworkUri = artworkPath
    )
}
