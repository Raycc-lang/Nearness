package com.yourname.nearness.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yourname.nearness.R

/**
 * Notification channels and display. Channels are created eagerly in
 * Application.onCreate() (never lazily) so a notification never arrives
 * before its channel exists.
 */
object NotificationHelper {

    const val CHANNEL_SIGNALS = "signals"
    const val CHANNEL_WHITEBOARD = "whiteboard"
    const val CHANNEL_STATUS = "status"

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

    fun show(context: Context, channelId: String, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        // POST_NOTIFICATIONS permission is requested at runtime in the UI layer.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
