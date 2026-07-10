package com.raycc.nearness.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.raycc.nearness.MainActivity
import com.raycc.nearness.R

/**
 * Notification channels and display. Channels are created eagerly in
 * Application.onCreate() (never lazily) so a notification never arrives
 * before its channel exists.
 */
object NotificationHelper {

    const val CHANNEL_SIGNALS = "signals"
    const val CHANNEL_WHITEBOARD = "whiteboard"
    const val CHANNEL_STATUS = "status"

    /** Intent extra key carrying the notification's target route (see contract). */
    const val EXTRA_ROUTE = "route"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SIGNALS, "Signals", NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Gentle one-tap signals from your partner" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WHITEBOARD, "Whiteboard", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "New items on your shared whiteboard" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS, "Status", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Partner status updates" },
        )
    }

    fun show(context: Context, channelId: String, title: String, body: String, route: String? = null) {
        // Unique per notification so distinct notifications keep distinct extras.
        val requestCode = System.currentTimeMillis().toInt()
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        // POST_NOTIFICATIONS permission is requested at runtime in the UI layer.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }
}
