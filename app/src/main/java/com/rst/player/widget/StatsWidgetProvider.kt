package com.rst.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.rst.player.MainActivity
import com.rst.player.R
import com.rst.player.data.db.RstDatabase
import com.rst.player.data.db.entity.PlayHistoryEntity
import com.rst.player.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class StatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidgetAsync(context, appWidgetManager, id)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAll(context)
    }

    companion object {
        // Widget work must never block the broadcast (main) thread. DB and
        // DataStore reads run on this dedicated background scope instead; the
        // process stays alive long enough for the (tiny) query to finish.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching {
                mgr.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
            }.getOrNull() ?: return
            for (id in ids) updateWidgetAsync(context, mgr, id)
        }

        private fun updateWidgetAsync(context: Context, mgr: AppWidgetManager, id: Int) {
            scope.launch {
                runCatching {
                    val views = buildViews(context)
                    mgr.updateAppWidget(id, views)
                }
            }
        }

        private suspend fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_stats)

            // Tap anywhere opens the app.
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_stats_label, open)
            views.setOnClickPendingIntent(R.id.widget_listen_time, open)
            views.setOnClickPendingIntent(R.id.widget_play_count, open)
            views.setOnClickPendingIntent(R.id.widget_top_song, open)

            val stats = queryStats(context)
            views.setTextViewText(R.id.widget_listen_time, formatTime(stats.listenMs))
            views.setTextViewText(R.id.widget_play_count, "${stats.totalPlays}")
            val top = stats.topSong
            if (top != null) {
                views.setTextViewText(R.id.widget_top_song, "Top: ${top.title}")
                views.setTextViewText(R.id.widget_top_artist, "${top.artist} · ${top.count}x")
            } else {
                views.setTextViewText(R.id.widget_top_song, "Play some music first")
                views.setTextViewText(R.id.widget_top_artist, "")
            }
            return views
        }

        private suspend fun queryStats(context: Context): WidgetStats = try {
            val db = RstDatabase.get(context)
            val dao = db.playHistoryDao()
            val settings = UserPreferences(context).settings.firstOrNull()
            WidgetStats(
                listenMs = settings?.totalListenMs ?: 0L,
                totalPlays = dao.size(),
                topSong = dao.mostPlayed(1).firstOrNull()
            )
        } catch (_: Exception) {
            WidgetStats(0L, 0, null)
        }

        private fun formatTime(ms: Long): String {
            val totalMin = ms / 60_000
            val h = totalMin / 60
            val m = totalMin % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }
}

private data class WidgetStats(
    val listenMs: Long,
    val totalPlays: Int,
    val topSong: PlayHistoryEntity?
)
