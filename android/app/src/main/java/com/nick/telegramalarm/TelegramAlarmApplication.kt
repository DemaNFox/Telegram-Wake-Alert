package com.nick.telegramalarm

import android.app.Application
import com.nick.telegramalarm.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TelegramAlarmApplication : Application() {
    @Inject lateinit var notificationChannels: NotificationChannels

    override fun onCreate() {
        super.onCreate()
        notificationChannels.create()
    }
}
