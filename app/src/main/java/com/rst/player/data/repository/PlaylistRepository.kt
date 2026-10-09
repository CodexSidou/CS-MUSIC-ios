package com.rst.player.data.repository

import com.rst.player.data.db.dao.PlaylistDao
import com.rst.player.data.db.dao.PlaylistSongDao
import com.rst.player.data.db.entity.PlaylistEntity
import com.rst.player.data.db.entity.PlaylistSongEntity
import com.rst.player.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val playlistSongDao: PlaylistSongDao
) {

    suspend fun createPlaylist(name: String): Long = playlistDao.insert(PlaylistEntity(name = name))

    suspend fun renamePlaylist(id: Long, name: String) {
        playlistDao.getById(id)?.let { playlistDao.rename(it.copy(name = name)) }
    }

    suspend fun deletePlaylist(id: Long) {
        if (id == favoritesId()) return
        playlistDao.getById(id)?.let { playlistDao.delete(it) }
        playlistSongDao.removeAll(id)
    }

    suspend fun getPlaylist(id: Long): PlaylistEntity? = playlistDao.getById(id)

    suspend fun findByName(name: String): PlaylistEntity? = playlistDao.findByName(name)

    /** Returns the playlist with [name], creating it if it doesn't exist yet. */
    suspend fun getOrCreateNamed(name: String): Long =
        playlistDao.findByName(name)?.id ?: playlistDao.insert(PlaylistEntity(name = name))

    fun observePlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observeAll()

    fun observePlaylistCounts(): Flow<Map<Long, Int>> =
        playlistSongDao.observeCounts().map { rows -> rows.associate { it.playlistId to it.count } }

    suspend fun addSong(playlistId: Long, song: Song) {
        val position = (playlistSongDao.maxPosition(playlistId) ?: -1) + 1
        playlistSongDao.insert(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id.toString(),
                title = song.title,
                artist = song.artist,
                album = song.album,
                albumArtUri = song.albumId.toString(),
                uri = song.uri.toString(),
                durationMs = song.durationMs,
                position = position
            )
        )
    }

    suspend fun removeSong(playlistId: Long, songId: String) {
        playlistSongDao.removeSong(playlistId, songId)
    }

    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongEntity>> =
        playlistSongDao.observeSongs(playlistId)

    suspend fun getSongs(playlistId: Long): List<PlaylistSongEntity> =
        playlistSongDao.getSongs(playlistId)

    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean =
        playlistSongDao.songIds(playlistId).contains(songId)

    // ---- Favorites (a real playlist under a reserved name) ----

    private suspend fun favoritesId(): Long =
        playlistDao.findByName(FAVORITES_NAME)?.id
            ?: playlistDao.insert(PlaylistEntity(name = FAVORITES_NAME))

    fun observeFavoritesSongs(): Flow<List<PlaylistSongEntity>> = flow {
        val id = favoritesId()
        emitAll(playlistSongDao.observeSongs(id))
    }

    suspend fun toggleFavorite(song: Song) {
        val id = favoritesId()
        if (playlistSongDao.songIds(id).contains(song.id.toString())) {
            playlistSongDao.removeSong(id, song.id.toString())
        } else {
            val position = (playlistSongDao.maxPosition(id) ?: -1) + 1
            playlistSongDao.insert(
                PlaylistSongEntity(
                    playlistId = id,
                    songId = song.id.toString(),
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    albumArtUri = song.albumId.toString(),
                    uri = song.uri.toString(),
                    durationMs = song.durationMs,
                    position = position
                )
            )
        }
    }

    suspend fun isFavorite(songId: String): Boolean =
        playlistSongDao.songIds(favoritesId()).contains(songId)

    suspend fun favoriteSongIds(): Set<String> = playlistSongDao.songIds(favoritesId()).toSet()

    companion object {
        const val FAVORITES_NAME = "__rst_favorites__"
    }
}
