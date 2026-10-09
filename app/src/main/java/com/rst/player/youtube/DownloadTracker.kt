package com.rst.player.youtube

import kotlinx.coroutines.flow.MutableStateFlow

class DownloadTracker {
    val active = MutableStateFlow<ActiveDownload?>(null)
    val done = MutableStateFlow<List<DownloadedItem>>(emptyList())

    fun resetActive() {
        active.value = null
    }
}
