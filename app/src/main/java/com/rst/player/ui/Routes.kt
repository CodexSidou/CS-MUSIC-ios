package com.rst.player.ui

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val TABS = "tabs"
    const val SEARCH = "search"
    const val NOW_PLAYING = "now_playing"
    const val MODES = "modes"
    const val MODE = "mode/{modeId}"
    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistName}"
    const val ONLINE_ARTIST = "online_artist/{artistId}?name={name}"
    const val ONLINE_ALBUM = "online_album/{albumId}?name={name}&artist={artist}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
}
