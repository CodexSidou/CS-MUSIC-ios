package com.rst.player.data.model

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val year: Int?
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val year: Int?
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int
)
