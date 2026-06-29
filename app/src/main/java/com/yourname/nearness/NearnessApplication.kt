package com.yourname.nearness

import android.app.Application
import com.yourname.nearness.service.NotificationHelper

class NearnessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Notification channels must exist before any notification is shown.
        NotificationHelper.createChannels(this)
    }
}
