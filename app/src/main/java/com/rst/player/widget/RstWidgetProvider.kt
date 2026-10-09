package com.rst.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.rst.player.MainActivity
import com.rst.player.R
import com.rst.player.player.PlaybackService

/**
 * Home-screen widget: album art, title/artist and prev/play-next controls.
 * The service pushes live state into it; buttons route straight to
 * PlaybackService via foreground-service intents.
 */
class RstWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
        // Ask the service to repaint with live state (it will also load artwork).
        // Use startForegroundService on O+ so a cold start from the widget can't
        // be refused as a background start; onStartCommand enters the foreground
        // within the allowed window.
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java).setAction(ACTION_UPDATE)
            )
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.rst.player.widget.TOGGLE"
        const val ACTION_NEXT = "com.rst.player.widget.NEXT"
        const val ACTION_PREVIOUS = "com.rst.player.widget.PREVIOUS"
        const val ACTION_UPDATE = "com.rst.player.widget.UPDATE"

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_rst)
            views.setTextViewText(R.id.widget_title, "RST Player")
            views.setTextViewText(R.id.widget_artist, "Nothing playing")

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_art, open)
            views.setOnClickPendingIntent(R.id.widget_title, open)

            views.setOnClickPendingIntent(R.id.widget_play, command(context, ACTION_TOGGLE, 1))
            views.setOnClickPendingIntent(R.id.widget_next, command(context, ACTION_NEXT, 2))
            views.setOnClickPendingIntent(R.id.widget_prev, command(context, ACTION_PREVIOUS, 3))
            return views
        }

        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, RstWidgetProvider::class.java))

        private fun command(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
            return PendingIntent.getForegroundService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
