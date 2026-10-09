package com.rst.player.data.repository

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.rst.player.data.mediastore.MediaStoreScanner
import com.rst.player.data.model.Album
import com.rst.player.data.model.Artist
import com.rst.player.data.model.Folder
import com.rst.player.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data class NeedsUserApproval(val intentSender: IntentSender) : DeleteResult
    data object Failed : DeleteResult
}

class MusicRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _lastScan = MutableStateFlow(0L)

    private val _albums = MutableStateFlow(emptyList<Album>())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow(emptyList<Artist>())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _folders = MutableStateFlow(emptyList<Folder>())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    /** Kicks off a background scan; results land on [songs] when done. Never blocks the caller. */
    fun scan() {
        scope.launch {
            try {
                scanNow()
            } catch (e: Exception) {
                _scanning.value = false
            }
        }
    }

    /** Runs a scan now and suspends until it completes. Safe to call from any thread. */
    suspend fun scanNow() {
        scanMutex.withLock {
            if (_scanning.value) return
            _scanning.value = true
            try {
                val result = MediaStoreScanner.scan(context)
                _songs.value = result
                _lastScan.value = System.currentTimeMillis()
                recomputeDerived(result)
            } finally {
                _scanning.value = false
            }
        }
    }

    private suspend fun recomputeDerived(songs: List<Song>) = withContext(Dispatchers.Default) {
        _albums.value = songs
            .groupBy { it.albumId }
            .map { (id, list) ->
                Album(
                    id = id,
                    name = list.groupingBy { it.album }.eachCount().maxByOrNull { it.value }?.key?.takeIf { it.isNotBlank() } ?: "Unknown Album",
                    artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: list.first().artist,
                    songCount = list.size,
                    year = list.firstNotNullOfOrNull { it.year }
                )
            }
            .sortedBy { it.name.lowercase() }

        _artists.value = songs
            .groupBy { it.artist.lowercase() }
            .map { (_, list) ->
                Artist(
                    name = list.first().artist,
                    songCount = list.size,
                    albumCount = list.map { it.album }.distinct().size,
                    year = list.firstNotNullOfOrNull { it.year }
                )
            }
            .sortedBy { it.name.lowercase() }

        _folders.value = songs
            .groupBy { File(it.dataPath).parentFile?.absolutePath ?: "/" }
            .map { (path, list) ->
                Folder(path = path, name = File(path).name.ifEmpty { path }, songCount = list.size)
            }
            .sortedBy { it.name.lowercase() }
    }

    fun findSongById(id: Long): Song? = _songs.value.find { it.id == id }
    fun findSongByStringId(id: String): Song? = _songs.value.find { it.id.toString() == id }

    /**
     * True if a song with the same title and artist (normalized) is already in
     * the library, so downloads can skip tracks the user already has. Titles and
     * artists are compared loosely ("Song (Official Audio)" matches "Song", and
     * "Artist - Topic" matches "Artist").
     */
    fun alreadyInLibrary(title: String, artist: String): Boolean {
        val wantTitle = normalizeTrackTitle(title)
        val wantArtist = normalizeArtistName(artist)
        return _songs.value.any { song ->
            normalizeTrackTitle(song.title) == wantTitle &&
                normalizeArtistName(song.artist) == wantArtist
        }
    }

    private fun normalizeTrackTitle(value: String): String {
        var t = value.trim().lowercase()
        val suffixes = listOf(
            " (official audio)", " (official video)", " (official music video)",
            " (official lyric video)", " (audio)", " (video)", " (lyrics)",
            " (lyric video)", " (music video)",
            " official audio", " official video", " official music video",
            " [official audio]", " [official video]", " [official music video]"
        )
        for (suffix in suffixes) {
            if (t.endsWith(suffix)) {
                t = t.removeSuffix(suffix).trim()
                break
            }
        }
        return t
    }

    private fun normalizeArtistName(value: String): String {
        var a = value.trim().lowercase()
        val suffixes = listOf(" - topic", " - official", " - official audio", " - audio", " official")
        for (suffix in suffixes) {
            if (a.endsWith(suffix)) {
                a = a.removeSuffix(suffix).trim()
                break
            }
        }
        return a
    }

    /**
     * Removes a song from the device (file + MediaStore entry).
     * If the phone won't let the app delete the file directly, it returns an
     * intent the UI must launch to ask the user for permission.
     */
    suspend fun deleteSong(song: Song): DeleteResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(song.uri, null, null)
            onSongDeleted(song.id)
            DeleteResult.Deleted
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= 29 && e is android.app.RecoverableSecurityException) {
                val sender = if (Build.VERSION.SDK_INT >= 30) {
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                        .intentSender
                } else {
                    @Suppress("DEPRECATION")
                    e.userAction.actionIntent.intentSender
                }
                DeleteResult.NeedsUserApproval(sender)
            } else {
                android.util.Log.e("MusicRepository", "delete failed", e)
                DeleteResult.Failed
            }
        }
    }

    /**
     * Removes a batch of songs from the device (files + MediaStore entries).
     * On API 30+ the whole batch is approved in a single system dialog via
     * [MediaStore.createDeleteRequest]; older versions fall back to the first
     * recoverable-security approval.
     */
    suspend fun deleteSongs(songs: List<Song>): DeleteResult = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext DeleteResult.Failed
        try {
            var needsApproval: android.app.RecoverableSecurityException? = null
            for (song in songs) {
                try {
                    context.contentResolver.delete(song.uri, null, null)
                    onSongDeleted(song.id)
                } catch (e: android.app.RecoverableSecurityException) {
                    needsApproval = e
                    break
                }
            }
            if (needsApproval == null) {
                DeleteResult.Deleted
            } else if (Build.VERSION.SDK_INT >= 30) {
                DeleteResult.NeedsUserApproval(
                    MediaStore.createDeleteRequest(context.contentResolver, songs.map { it.uri })
                        .intentSender
                )
            } else {
                @Suppress("DEPRECATION")
                DeleteResult.NeedsUserApproval(needsApproval.userAction.actionIntent.intentSender)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "bulk delete failed", e)
            DeleteResult.Failed
        }
    }

    /**
     * Drops a song from the in-memory library the moment it is deleted.
     * MediaStore row removal can lag behind a user-approved delete, so the UI
     * must never depend on a rescan to hide a deleted song.
     */
    fun onSongDeleted(id: Long) {
        if (_songs.value.none { it.id == id }) return
        val updated = _songs.value.filterNot { it.id == id }
        _songs.value = updated
        scope.launch { recomputeDerived(updated) }
    }

    /**
     * Best-effort artist picture: the album art of the artist's first album.
     * The old MediaStore "artistalbumart" provider is broken on modern Android,
     * so we derive art from albums we already know about instead.
     */
    fun artistArtUri(artist: String): android.net.Uri? {
        if (artist.isBlank() || artist == Song.UNKNOWN_ARTIST) return null
        val albumId = _songs.value
            .asSequence()
            .filter { it.artist.equals(artist, ignoreCase = true) && it.albumId > 0 }
            .map { it.albumId }
            .firstOrNull() ?: return null
        return MediaStoreScanner.albumArtUri(albumId)
    }

    fun songsInFolder(path: String): List<Song> =
        _songs.value.filter { File(it.dataPath).parentFile?.absolutePath == path }

    fun songsByAlbum(albumId: Long): List<Song> =
        _songs.value.filter { it.albumId == albumId }

    fun songsByArtist(name: String): List<Song> =
        _songs.value.filter { it.artist.equals(name, ignoreCase = true) }

    fun searchSongs(query: String): List<Song> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return _songs.value.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
        }
    }

    fun searchAlbums(query: String): List<Album> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return _albums.value.filter {
            it.name.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    fun searchArtists(query: String): List<Artist> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return _artists.value.filter { it.name.lowercase().contains(q) }
    }
}
