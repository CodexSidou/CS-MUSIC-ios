package com.rst.player.youtube

data class YouTubeVideo(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Long,
    val thumbnail: String,
    val webpageUrl: String,
    val viewCount: Long = 0
)

data class ActiveDownload(
    val videoId: String,
    val title: String,
    val progress: Float,
    val state: String
)

data class YouTubeChannel(
    val name: String,
    val thumbnail: String,
    val channelUrl: String,
    val subscriberCount: String,
    val artistId: Long? = null,
    val isVerified: Boolean = false
)

data class DownloadedItem(
    val title: String,
    val path: String,
    val addedAt: Long
)
