package com.rst.player.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rst.player.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackUiState(
    val controller: MediaController? = null,
    val isPlaying: Boolean = false,
    val currentMediaId: String? = null,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackState: Int = Player.STATE_IDLE,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentArtworkUrl: String? = null
)

/** A streamable song that lives online (played from cache, not in the library). */
data class OnlineTrack(
    val mediaId: String,
    val title: String,
    val artist: String,
    val uri: String,
    val artworkUri: String? = null
)

class PlayerController(context: Context) {

    private val appContext = context.applicationContext

    private val _ui = MutableStateFlow(PlaybackUiState())
    val ui: StateFlow<PlaybackUiState> = _ui.asStateFlow()

    private var controller: MediaController? = null
    private var pendingPlay: (() -> Unit)? = null

    // Artwork for online tracks, keyed by media id. Media3 usually mirrors the
    // artworkUri we set on the MediaItem, but the session round-trip can drop it
    // (or sync fires before metadata attaches) — this map guarantees the picture
    // still shows in the mini player / now playing.
    private val artworkByMediaId = mutableMapOf<String, String>()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_PLAYER_ERROR
                )
            ) {
                sync()
            }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                sync()
                pendingPlay?.let { pendingPlay = null; it() }
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun release() {
        controller?.release()
        controller = null
    }

    private fun sync() {
        val c = controller ?: return
        val metadata = c.currentMediaItem?.mediaMetadata
        _ui.value = PlaybackUiState(
            controller = c,
            isPlaying = c.isPlaying,
            currentMediaId = c.currentMediaItem?.mediaId,
            durationMs = c.duration,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            playbackState = c.playbackState,
            currentTitle = metadata?.title?.toString().orEmpty(),
            currentArtist = metadata?.artist?.toString().orEmpty(),
            currentArtworkUrl = metadata?.artworkUri?.toString()
                ?.takeIf { it.startsWith("http") || it.startsWith("file") }
                ?: c.currentMediaItem?.mediaId?.let { artworkByMediaId[it] }
        )
    }

    // ---- Playback commands ----

    fun playQueue(songs: List<Song>, startIndex: Int, shuffle: Boolean = false, startPositionMs: Long = 0L) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        val c = controller
        if (c == null) {
            pendingPlay = { playQueue(songs, safeIndex, shuffle, startPositionMs) }
            return
        }
        runCatching {
            c.stop()
            c.setShuffleModeEnabled(shuffle)
            c.setMediaItems(songs.map { it.toMediaItem() }, safeIndex, startPositionMs)
            c.prepare()
            c.play()
        }.onFailure { t ->
            android.util.Log.e("PlayerController", "playQueue failed", t)
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    /** Plays online (non-library) tracks — cached previews resolved from search. */
    fun playOnline(items: List<OnlineTrack>, startIndex: Int, shuffle: Boolean = false) {
        if (items.isEmpty()) return
        items.forEach { track ->
            track.artworkUri?.takeIf { it.isNotBlank() }?.let { artworkByMediaId[track.mediaId] = it }
        }
        val safeIndex = startIndex.coerceIn(0, items.size - 1)
        val c = controller
        if (c == null) {
            pendingPlay = { playOnline(items, safeIndex, shuffle) }
            return
        }
        runCatching {
            c.stop()
            c.setShuffleModeEnabled(shuffle)
            c.setMediaItems(items.map { it.toMediaItem() }, safeIndex, 0L)
            c.prepare()
            c.play()
        }.onFailure { t ->
            android.util.Log.e("PlayerController", "playOnline failed", t)
        }
    }

    /** Appends more online tracks to the running queue (used by artist play-all). */
    fun appendOnline(items: List<OnlineTrack>) {
        if (items.isEmpty()) return
        items.forEach { track ->
            track.artworkUri?.takeIf { it.isNotBlank() }?.let { artworkByMediaId[track.mediaId] = it }
        }
        val c = controller ?: return
        runCatching {
            c.addMediaItems(items.map { it.toMediaItem() })
        }.onFailure { t ->
            android.util.Log.e("PlayerController", "appendOnline failed", t)
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        runCatching {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        controller?.repeatMode = mode
    }

    fun stop() {
        controller?.stop()
    }

    fun isCurrent(songId: String): Boolean = _ui.value.currentMediaId == songId

    fun currentSongId(): String? = _ui.value.currentMediaId
}
