package com.nick.telegramalarm.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nick.telegramalarm.R
import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.presentation.MainActivity
import com.nick.telegramalarm.presentation.alarm.AlarmActivity
import com.nick.telegramalarm.service.AlarmForegroundService
import com.nick.telegramalarm.service.ServiceActions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun foreground(status: String) = NotificationCompat.Builder(context, NotificationChannels.FOREGROUND)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Telegram Alarm Bot Active")
        .setContentText(status)
        .setOngoing(true)
        .setShowWhen(false)
        .setContentIntent(mainPendingIntent())
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    fun alarm(event: AlarmEvent) = NotificationCompat.Builder(context, NotificationChannels.ALARM)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(event.notificationTitle())
        .setContentText(event.message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setAutoCancel(true)
        .setDefaults(0)
        .setSilent(true)
        .setFullScreenIntent(alarmPendingIntent(event), true)
        .setContentIntent(alarmPendingIntent(event))
        .addAction(R.mipmap.ic_launcher, "Stop", serviceActionPendingIntent(ServiceActions.STOP_ALARM, 31))
        .addAction(R.mipmap.ic_launcher, "Sleep 5", serviceActionPendingIntent(ServiceActions.SNOOZE_FIVE_MINUTES, 32))
        .addAction(R.mipmap.ic_launcher, "Sleep 10", serviceActionPendingIntent(ServiceActions.SNOOZE_TEN_MINUTES, 33))
        .build()

    fun connectionLost(minutes: Long) = NotificationCompat.Builder(context, NotificationChannels.FOREGROUND)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Telegram Alarm disconnected")
        .setContentText("Backend connection has been down for $minutes min")
        .setContentIntent(mainPendingIntent())
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun alarmPendingIntent(event: AlarmEvent): PendingIntent {
        val intent = AlarmActivity.intent(context, event)
        return PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmForegroundService::class.java).setAction(action)
        return PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun AlarmEvent.notificationTitle(): String =
        chatTitle?.takeIf { it.isNotBlank() }?.let { "$senderName - $it" } ?: senderName
}
