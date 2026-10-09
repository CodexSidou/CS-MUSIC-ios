package com.rst.player.data.model

/**
 * A play mode (Car, Gym, Sleep or a user-created one). When [playlistId]
 * is set the mode plays that curated playlist; otherwise it builds a
 * smart queue from the library.
 */
data class PlayerMode(
    val id: String,
    val name: String,
    val iconKey: String,
    val isBuiltin: Boolean = false,
    val playlistId: Long? = null
)
