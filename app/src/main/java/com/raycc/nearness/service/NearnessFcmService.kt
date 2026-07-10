package com.raycc.nearness.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM pushes and shows them on the matching channel.
 * Channel id is sent by the send-notification Edge Function.
 */
class NearnessFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        // Prefer channel_id from data payload (set by Edge Function), fall back to
        // notification.channelId which may be null on some FCM SDK versions.
        val channelId = message.data["channel_id"]
            ?: notification.channelId
            ?: NotificationHelper.CHANNEL_STATUS
        NotificationHelper.show(
            context = this,
            channelId = channelId,
            title = notification.title ?: "Nearness",
            body = notification.body.orEmpty(),
            route = message.data["route"],
        )
    }

    /**
     * Token rotated. The new token is persisted on next app launch via
     * ProfileRepository.updateFcmToken (we can't safely call the network
     * here without an authenticated session).
     */
    override fun onNewToken(token: String) {
        // No-op: the app upserts the current token on launch.
    }
}
