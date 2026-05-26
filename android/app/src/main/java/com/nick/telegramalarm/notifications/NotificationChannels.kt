package com.nick.telegramalarm.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun create() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(FOREGROUND, "Telegram Alarm Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent background connection"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALARM, "Telegram Alarm Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen alarm alerts"
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    companion object {
        const val FOREGROUND = "telegram_alarm_foreground"
        const val ALARM = "telegram_alarm_alarm_silent_v2"
    }
}
