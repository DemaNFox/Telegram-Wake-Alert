package com.nick.telegramalarm

import android.app.Application
import com.nick.telegramalarm.notifications.NotificationChannels
import com.nick.telegramalarm.push.PushRegistrationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TelegramAlarmApplication : Application() {
    @Inject lateinit var notificationChannels: NotificationChannels
    @Inject lateinit var pushRegistrationManager: PushRegistrationManager

    override fun onCreate() {
        super.onCreate()
        notificationChannels.create()
        pushRegistrationManager.refresh()
    }
}
