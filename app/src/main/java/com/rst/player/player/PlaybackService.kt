package com.rst.player.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.rst.player.RstApplication
import com.rst.player.MainActivity
import com.rst.player.R
import com.rst.player.data.mediastore.MediaStoreScanner
import com.rst.player.widget.RstWidgetProvider
import com.rst.player.widget.StatsWidgetProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var lastSongId: String? = null
    private var endedHandled = false

    private val audioManager: AudioManager by lazy {
        getSystemService(AudioManager::class.java)
    }
    private var wasPlayingBeforeFocusLoss = false
    private var ducked = false
    private var focusRequest: AudioFocusRequest? = null

    /**
     * We handle audio focus ourselves (rather than Media3's built-in manager) so
     * that when another app takes focus (e.g. a TikTok video) the music pauses,
     * and automatically resumes the moment that app stops. Media3's built-in
     * manager pauses on a permanent focus loss but never comes back.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (ducked) {
                    player?.volume = 1f
                    ducked = false
                }
                if (wasPlayingBeforeFocusLoss) {
                    wasPlayingBeforeFocusLoss = false
                    player?.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeFocusLoss = player?.isPlaying == true
                if (wasPlayingBeforeFocusLoss) player?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                wasPlayingBeforeFocusLoss = player?.isPlaying == true
                if (wasPlayingBeforeFocusLoss) {
                    player?.volume = 0.25f
                    ducked = true
                }
            }
        }
    }

    private fun requestAudioFocus() {
        runCatching {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(false)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(req)
        }
    }

    private fun abandonAudioFocus() {
        runCatching {
            val req = focusRequest ?: return@runCatching
            audioManager.abandonAudioFocusRequest(req)
        }
    }

    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, t ->
                Log.e(TAG, "background task failed", t)
            }
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastTickPosition = 0L

    private val listenTicker = object : Runnable {
        override fun run() {
            flushListenTime()
            mainHandler.postDelayed(this, 5000)
        }
    }

    private fun flushListenTime() {
        val p = player ?: return
        if (!p.isPlaying) {
            lastTickPosition = p.currentPosition
            return
        }
        val delta = p.currentPosition - lastTickPosition
        lastTickPosition = p.currentPosition
        if (delta > 0 && delta < 600_000L) {
            val ms = delta
            scope.launch {
                try {
                    RstApplication.graphOf(this@PlaybackService)
                        .userPreferences.addListenTime(ms)
                } catch (t: Throwable) {
                    Log.e(TAG, "addListenTime failed", t)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        createPlaybackChannel()

        val p = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // handleAudioFocus = false: we run our own focus listener so the
                // music can auto-resume after another app stops.
                false
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = p

        scope.launch {
            try {
                val graph = RstApplication.graphOf(this@PlaybackService)
                val settings = graph.userPreferences.settings.first()
                p.setHandleAudioBecomingNoisy(settings.headphonePause)
            } catch (t: Throwable) {
                Log.e(TAG, "load settings failed", t)
            }
        }

        mediaSession = MediaSession.Builder(this, p)
            .setSessionActivity(sessionActivityIntent())
            .build()

        setMediaNotificationProvider(notificationProvider)

        updateWidget()

        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId
                if (id != null) lastSongId = id
                endedHandled = false
                if (id != null) recordHistory(id)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lastTickPosition = p.currentPosition
                if (!isPlaying) flushListenTime()
                // Take audio focus whenever we start playing, and release it when
                // the user pauses/stops. A pause triggered by focus loss (another
                // app taking over) leaves focus held so we can auto-resume.
                if (isPlaying) {
                    requestAudioFocus()
                } else if (!wasPlayingBeforeFocusLoss) {
                    abandonAudioFocus()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "playback error: $error")
                // Attempt recovery for transient errors — prepares the player again
                // so the user can press Play rather than getting a stuck/crashed state.
                mainHandler.postDelayed({
                    try {
                        val p2 = player ?: return@postDelayed
                        if (p2.playbackState == Player.STATE_IDLE) {
                            p2.prepare()
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "error recovery failed", t)
                    }
                }, 500)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    flushListenTime()
                    if (!endedHandled) {
                        endedHandled = true
                        maybeAutoplayNext()
                    }
                }
            }
        })

        mainHandler.post(listenTicker)
    }

    private val notificationProvider = object : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            mediaButtons: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            callback: MediaNotification.Provider.Callback
        ): MediaNotification {
            val notification = buildNotification(mediaSession, actionFactory)
            return MediaNotification(NOTIFICATION_ID, notification)
        }

        override fun handleCustomCommand(
            mediaSession: MediaSession,
            customCommand: String,
            extras: Bundle
        ): Boolean = false
    }

    private fun createPlaybackChannel() {
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Now playing controls"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(
        mediaSession: MediaSession,
        actionFactory: MediaNotification.ActionFactory
    ): android.app.Notification {
        val p = mediaSession.player
        val metadata = p.mediaMetadata
        val title = metadata.title?.toString()?.ifEmpty { "RST Player" } ?: "RST Player"
        val artist = metadata.artist?.toString()?.ifEmpty { "Now playing" } ?: "Now playing"
        val artwork = metadata.artworkData?.let { decodeArtwork(it) }
        val isPlaying = p.isPlaying

        val builder = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(mediaSession.sessionActivity)
            .setLargeIcon(artwork)
            .setOnlyAlertOnce(true)
            .setColor(0xFF6366F1.toInt())
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (p.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) {
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(this, R.drawable.ic_prev),
                    "Previous",
                    Player.COMMAND_SEEK_TO_PREVIOUS
                )
            )
        }

        val playPauseIcon = if (isPlaying) {
            IconCompat.createWithResource(this, R.drawable.ic_pause)
        } else {
            IconCompat.createWithResource(this, R.drawable.ic_play)
        }
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                playPauseIcon,
                if (isPlaying) "Pause" else "Play",
                Player.COMMAND_PLAY_PAUSE
            )
        )

        if (p.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) {
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(this, R.drawable.ic_next),
                    "Next",
                    Player.COMMAND_SEEK_TO_NEXT
                )
            )
        }

        // The notification rebuilds on every media transition / play state
        // change, so it is a perfect hook to keep the widget in sync too.
        updateWidget()

        return builder.build()
    }

    private fun sessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun recordHistory(songId: String) {
        scope.launch {
            try {
                val graph = RstApplication.graphOf(this@PlaybackService)
                val song = graph.musicRepository.findSongByStringId(songId)
                if (song != null) {
                    graph.historyRepository.recordPlay(song.id.toString(), song.title, song.artist)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "recordHistory failed", t)
            }
        }
    }

    private fun maybeAutoplayNext() {
        val graph = RstApplication.graphOf(this)
        val p = player ?: return
        scope.launch {
            try {
                val settings = graph.userPreferences.settings.first()
                if (!settings.autoplay) return@launch
                // Streamed previews are a one-off queue — don't chain into the library.
                if (lastSongId?.startsWith("preview-") == true) return@launch
                val songs = graph.musicRepository.songs.value
                if (songs.isEmpty()) return@launch
                val seed = songs.find { it.id.toString() == lastSongId }
                val existing = buildSet {
                    val timeline = p.currentTimeline
                    val window = Timeline.Window()
                    for (i in 0 until timeline.windowCount) {
                        timeline.getWindow(i, window)
                        window.mediaItem.mediaId?.let { add(it) }
                    }
                }
                val picks = withContext(Dispatchers.Default) {
                    if (seed != null) {
                        graph.recommendationEngine.smartNext(seed, 30, existing)
                    } else {
                        graph.recommendationEngine.madeForYou(30, existing)
                    }
                }
                if (picks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        runCatching {
                            p.addMediaItems(picks.map { it.toMediaItem() })
                            p.play()
                        }.onFailure { t -> Log.e(TAG, "autoplay failed", t) }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "maybeAutoplayNext failed", t)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            RstWidgetProvider.ACTION_TOGGLE -> runCatching {
                val p = player ?: return@runCatching
                if (p.isPlaying) p.pause() else p.play()
            }
            RstWidgetProvider.ACTION_NEXT -> runCatching { player?.seekToNextMediaItem() }
            RstWidgetProvider.ACTION_PREVIOUS -> runCatching {
                val p = player ?: return@runCatching
                if (p.currentPosition > 3000) p.seekTo(0L) else p.seekToPreviousMediaItem()
            }
            RstWidgetProvider.ACTION_UPDATE -> updateWidget()
        }
        if (action != null) {
            // A widget command can cold-start the service; satisfy the foreground
            // timeout so the system doesn't kill us before Media3 posts its
            // notification.
            ensureForeground()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private var ensuredForeground = false

    private fun ensureForeground() {
        if (ensuredForeground) return
        ensuredForeground = true
        runCatching {
            val notif = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("RST Player")
                .setContentText("Music player")
                .setOngoing(false)
                .build()
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    /** Pushes live title/artist + play-pause state into the home-screen widget,
     *  then loads the album art in the background. */
    private fun updateWidget() {
        runCatching {
            val ids = RstWidgetProvider.widgetIds(this)
            if (ids.isEmpty()) return@runCatching
            val p = player ?: return@runCatching
            val metadata = p.mediaMetadata
            val title = metadata.title?.toString()?.ifEmpty { "RST Player" } ?: "RST Player"
            val artist = metadata.artist?.toString()?.ifEmpty { "Now playing" } ?: "Now playing"
            val views = RstWidgetProvider.buildViews(this)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_play,
                if (p.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            AppWidgetManager.getInstance(this).updateAppWidget(ids, views)
            loadWidgetArtwork(ids, p.isPlaying)
        }
        runCatching { StatsWidgetProvider.updateAll(this) }
    }

    private fun loadWidgetArtwork(ids: IntArray, isPlaying: Boolean) {
        val p = player ?: return
        val mediaId = p.currentMediaItem?.mediaId ?: return
        scope.launch {
            try {
                val graph = RstApplication.graphOf(this@PlaybackService)
                val song = graph.musicRepository.findSongByStringId(mediaId)
                // Decode on a background thread — never on the main dispatcher.
                val bmp = withContext(Dispatchers.IO) {
                    val uri = song?.let { MediaStoreScanner.albumArtUri(it.albumId) }
                    uri?.let { loadWidgetBitmap(it) }
                }
                if (bmp != null) {
                    val views = RstWidgetProvider.buildViews(this@PlaybackService)
                    views.setImageViewBitmap(R.id.widget_art, bmp)
                    views.setImageViewResource(
                        R.id.widget_play,
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                    runCatching {
                        AppWidgetManager.getInstance(this@PlaybackService).updateAppWidget(ids, views)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "widget artwork failed", t)
            }
        }
    }

    private fun loadWidgetBitmap(uri: Uri): android.graphics.Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 256) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        } catch (_: Throwable) {
            null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Only stop the service if we are not actively playing AND the queue is empty.
        // Using && so that a non-empty queue (even paused) keeps the service alive.
        val shouldStop = player?.playWhenReady != true && (player?.mediaItemCount ?: 0) == 0
        if (shouldStop) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        flushListenTime()
        mainHandler.removeCallbacks(listenTicker)
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    /**
     * Decodes embedded artwork downsampled to at most MAX_ART_SIZE px on its
     * longest side. Prevents OutOfMemoryError / ANR / TransactionTooLargeException
     * crashes that happen when huge album art reaches the notification.
     */
    private fun decodeArtwork(data: ByteArray): android.graphics.Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_ART_SIZE) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val PLAYBACK_CHANNEL_ID = "playback"
        private const val TAG = "PlaybackService"
        private const val MAX_ART_SIZE = 512
    }
}
