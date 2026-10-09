package com.rst.player.ui.viewmodel

import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rst.player.player.OnlineTrack
import com.rst.player.player.PlayerController
import com.rst.player.youtube.YouTubeChannel
import com.rst.player.youtube.YouTubeRepository
import com.rst.player.youtube.YouTubeVideo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ExploreTab { SONGS, ALBUMS }

class ExploreViewModel(
    private val youTubeRepository: YouTubeRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _trending = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val trending: StateFlow<List<YouTubeVideo>> = _trending.asStateFlow()

    private val _topAlbums = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val topAlbums: StateFlow<List<YouTubeVideo>> = _topAlbums.asStateFlow()

    private val _chartTab = MutableStateFlow(ExploreTab.SONGS)
    val chartTab: StateFlow<ExploreTab> = _chartTab.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    private val _region = MutableStateFlow("")
    val region: StateFlow<String> = _region.asStateFlow()

    private val _regionLabel = MutableStateFlow("")
    val regionLabel: StateFlow<String> = _regionLabel.asStateFlow()

    // Search state (same engine as the Search screen).
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _results = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val results: StateFlow<List<YouTubeVideo>> = _results.asStateFlow()

    private val _albums = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val albums: StateFlow<List<YouTubeVideo>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<YouTubeChannel>>(emptyList())
    val artists: StateFlow<List<YouTubeChannel>> = _artists.asStateFlow()

    private val _lastQuery = MutableStateFlow("")
    val lastQuery: StateFlow<String> = _lastQuery.asStateFlow()

    private val _previewingId = MutableStateFlow<String?>(null)
    val previewingId: StateFlow<String?> = _previewingId.asStateFlow()

    init {
        viewModelScope.launch {
            val regionCode = LocaleList.getDefault().get(0)?.country ?: "US"
            _region.value = regionCode.ifBlank { "US" }
            _regionLabel.value = regionLabelFor(_region.value)
            refresh()
            while (isActive) {
                delay(3 * 60 * 1000L)
                refresh()
            }
        }
    }

    fun setChartTab(tab: ExploreTab) {
        if (_chartTab.value != tab) _chartTab.value = tab
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _searching.value = true
            _error.value = null
            youTubeRepository.search(q)
            _results.value = youTubeRepository.results.value
            _albums.value = youTubeRepository.albumResults.value
            _artists.value = youTubeRepository.artists.value
            _searching.value = false
            _lastQuery.value = q
        }
    }

    fun clearSearch() {
        _query.value = ""
        _results.value = emptyList()
        _albums.value = emptyList()
        _artists.value = emptyList()
        _lastQuery.value = ""
        _error.value = null
    }

    fun refreshAsync() {
        viewModelScope.launch { refresh() }
    }

    /** Loads the next page of chart songs when the user scrolls near the bottom. */
    fun loadMore() {
        if (_loadingMore.value || !_hasMore.value) return
        if (_lastQuery.value.isNotBlank() || _chartTab.value != ExploreTab.SONGS) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val more = youTubeRepository.moreTrending(_region.value, _trending.value.size)
                if (more.isEmpty()) {
                    _hasMore.value = false
                } else {
                    val known = _trending.value.map { it.id }.toSet()
                    _trending.value = _trending.value + more.filter { it.id !in known }
                }
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /** Reloads the songs + albums charts and suspends until the pass finishes. */
    suspend fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        _loading.value = _trending.value.isEmpty() && _topAlbums.value.isEmpty()
        _error.value = null
        val startedEmpty = _loading.value
        // Never let the initial shimmer sit indefinitely while slow chart
        // sources trickle in; the empty state shows instead and content
        // replaces it the moment either list arrives.
        if (startedEmpty) {
            viewModelScope.launch {
                delay(12_000)
                _loading.value = false
            }
        }
        // Hard ceiling on the "refreshing" state too: a slow source must never
        // keep the header spinner (or the disabled pull gesture) up forever.
        viewModelScope.launch {
            delay(15_000)
            _refreshing.value = false
        }
        try {
            val (songs, albums) = coroutineScope {
                val a = async { youTubeRepository.trending(_region.value) }
                val b = async { youTubeRepository.topAlbums(_region.value) }
                a.await() to b.await()
            }
            if (songs.isNotEmpty()) _trending.value = songs
            if (albums.isNotEmpty()) _topAlbums.value = albums
            _hasMore.value = songs.isNotEmpty()
            _lastUpdated.value = System.currentTimeMillis()
            if (songs.isEmpty() && albums.isEmpty()) {
                _error.value = "Couldn't load charts. Check your connection and pull to refresh."
            }
        } catch (e: Exception) {
            if (_trending.value.isEmpty() && _topAlbums.value.isEmpty()) {
                _error.value = e.message ?: "Couldn't load charts."
            }
        } finally {
            _loading.value = false
            _refreshing.value = false
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
        val videos = if (_lastQuery.value.isNotBlank()) _results.value else _trending.value
        if (videos.isEmpty()) return
        viewModelScope.launch {
            for ((i, video) in videos.withIndex()) {
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

    fun downloadAll(videos: List<YouTubeVideo>) = youTubeRepository.runDownloads(videos)

    private fun regionLabelFor(code: String): String {
        return try {
            java.util.Locale("", code).displayCountry
        } catch (_: Exception) {
            code
        }
    }

    private fun com.rst.player.youtube.YouTubeRepository.PreviewResult.toTrack() = OnlineTrack(
        mediaId = mediaId,
        title = title,
        artist = artist,
        uri = filePath,
        artworkUri = artworkPath
    )
}
