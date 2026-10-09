package com.rst.player.desktop

import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Desktop audio engine built on JavaFX Media — plays MP3 / M4A-AAC / WAV out
 * of the box with no external codec installs. JavaFX toolkit is started in
 * headless mode (Platform.startup) so no JavaFX window is ever shown; only its
 * media pipeline is used.
 */
class PlayerEngine {

    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    val playing = MutableStateFlow(false)
    val nowPlayingPath = MutableStateFlow<String?>(null)

    /** Invoked (on a background thread) when the current track finishes. */
    @Volatile
    var onTrackEnd: (() -> Unit)? = null

    @Volatile
    private var ready = false

    private var player: MediaPlayer? = null

    @Volatile
    private var volume = 0.85

    fun startToolkit() {
        try {
            Platform.startup { ready = true }
            ready = true
        } catch (_: IllegalStateException) {
            // Toolkit already running from a previous call.
            ready = true
        } catch (t: Throwable) {
            println("RST Player: JavaFX media unavailable (${t.message}); playback disabled")
        }
    }

    fun available(): Boolean = ready

    suspend fun play(file: File) = withContext(Dispatchers.IO) {
        stop()
        if (!ready) return@withContext
        Platform.runLater {
            try {
                val p = MediaPlayer(Media(file.toURI().toString()))
                p.volume = volume
                p.currentTimeProperty().addListener { _, _, nv ->
                    positionMs.value = nv.toMillis().toLong()
                }
                p.setOnReady {
                    durationMs.value = p.media.duration.toMillis().toLong()
                    p.play()
                }
                p.setOnEndOfMedia { onTrackEnd?.let { cb -> Thread(cb).start() } }
                p.setOnError { playing.value = false }
                player = p
                nowPlayingPath.value = file.absolutePath
                playing.value = true
            } catch (t: Throwable) {
                playing.value = false
                println("RST Player: cannot play ${file.name}: ${t.message}")
            }
        }
    }

    fun resume() {
        Platform.runLater { player?.play() }
        playing.value = true
    }

    fun pause() {
        Platform.runLater { player?.pause() }
        playing.value = false
    }

    fun toggle() = if (playing.value) pause() else resume()

    fun seek(ms: Long) {
        Platform.runLater { player?.seek(Duration.millis(ms.toDouble())) }
        positionMs.value = ms
    }
    fun setVolume(v: Double) {
        volume = v.coerceIn(0.0, 1.0)
        Platform.runLater { player?.volume = volume }
    }

    fun stopCurrent() {
        Platform.runLater {
            try { player?.stop() } catch (_: Throwable) {}
            try { player?.dispose() } catch (_: Throwable) {}
            player = null
        }
        playing.value = false
        positionMs.value = 0L
        durationMs.value = 0L
        nowPlayingPath.value = null
    }
}
