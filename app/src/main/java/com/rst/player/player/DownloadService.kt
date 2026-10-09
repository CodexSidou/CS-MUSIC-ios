package com.rst.player.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rst.player.RstApplication
import com.rst.player.MainActivity
import com.rst.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var downloading = false
    private var isSpotify = false
    private var isPlaylist = false
    private var titleHint = "YouTube audio"
    private var qualityIndex = 0
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                downloadJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val url = intent?.getStringExtra(EXTRA_URL)
                if (url.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (downloading) return START_NOT_STICKY
                downloading = true
                titleHint = intent.getStringExtra(EXTRA_TITLE) ?: "YouTube audio"
                qualityIndex = intent.getIntExtra(EXTRA_QUALITY, 0)
                isSpotify = intent.getBooleanExtra(EXTRA_IS_SPOTIFY, false)
                isPlaylist = intent.getBooleanExtra(EXTRA_IS_PLAYLIST, false)
                startDownload(url)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String) {
        val qualityLabel = qualityLabel(qualityIndex)
        startForegroundCompat(
            NOTIFICATION_ID,
            buildProgressNotification(titleHint, qualityLabel, 0f, "Preparing download engine…")
        )
        val repo = RstApplication.graphOf(this).youTubeRepository
        downloadJob = scope.launch {
            val onProgress: (Float, String) -> Unit = { progress, state ->
                // yt-dlp can fire progress callbacks every few dozen ms; re-posting a
                // foreground notification that often makes the whole system stutter.
                // Throttle to ~4/sec and always pass through the terminal "Done" state.
                val now = android.os.SystemClock.elapsedRealtime()
                if (progress >= 1f || now - lastNotifyAt >= 250L) {
                    lastNotifyAt = now
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildProgressNotification(titleHint, qualityLabel, progress, state)
                    )
                }
            }
            val onError: (String) -> Unit = { message ->
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildProgressNotification(titleHint, qualityLabel, 0f, "Error")
                )
                finishNotification(
                    title = "Download failed",
                    text = message
                )
            }
            val onComplete: (String, String) -> Unit = { title, artist ->
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildProgressNotification(titleHint, qualityLabel, 1f, "Done")
                )
                finishNotification(
                    title = "Download complete",
                    text = "$title · $artist"
                )
            }
            when {
                isSpotify -> repo.downloadSpotifyUrl(url, qualityIndex, onProgress, onError, onComplete)
                isPlaylist -> repo.downloadPlaylist(
                    url,
                    qualityIndex,
                    onProgress,
                    onError,
                    onComplete = { downloaded, total ->
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildProgressNotification(titleHint, qualityLabel, 1f, "Done")
                        )
                        finishNotification(
                            title = "Playlist download complete",
                            text = "$downloaded of $total songs saved"
                        )
                    }
                )
                else -> repo.downloadFromUrl(url, titleHint, qualityIndex, onProgress, onError, onComplete)
            }
            stopSelf()
        }
    }

    private fun startForegroundCompat(id: Int, notification: android.app.Notification) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(
                id,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(id, notification)
        }
    }

    private fun finishNotification(title: String, text: String) {
        val pi = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFF6366F1.toInt())
            .setColorized(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notif)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildProgressNotification(title: String, quality: String, progress: Float, state: String): android.app.Notification {
        val stopPi = PendingIntent.getService(
            this,
            2,
            Intent(this, DownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading $title · $quality")
            .setContentText("$percent% • $state")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF6366F1.toInt())
            .setColorized(true)
            .setProgress(100, percent, false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPi
            )
            .build()
    }

    override fun onDestroy() {
        downloading = false
        scope.cancel()
        super.onDestroy()
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "YouTube downloads"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 2001

        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_IS_SPOTIFY = "extra_is_spotify"
        const val EXTRA_IS_PLAYLIST = "extra_is_playlist"
        const val ACTION_STOP = "com.rst.player.download.STOP"

        fun start(context: android.content.Context, url: String, title: String, qualityIndex: Int) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_QUALITY, qualityIndex)
                .putExtra(EXTRA_IS_SPOTIFY, false)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startSpotify(context: android.content.Context, url: String, qualityIndex: Int) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, "Spotify track")
                .putExtra(EXTRA_QUALITY, qualityIndex)
                .putExtra(EXTRA_IS_SPOTIFY, true)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startPlaylist(context: android.content.Context, url: String, qualityIndex: Int) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, "Playlist")
                .putExtra(EXTRA_QUALITY, qualityIndex)
                .putExtra(EXTRA_IS_PLAYLIST, true)
            ContextCompat.startForegroundService(context, intent)
        }

        fun qualityLabel(index: Int): String = when (index) {
            1 -> "High quality"
            2 -> "Medium quality"
            3 -> "Low quality"
            else -> "Highest quality"
        }
    }
}
