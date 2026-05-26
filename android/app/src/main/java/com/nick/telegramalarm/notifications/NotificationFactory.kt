package com.nick.telegramalarm.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nick.telegramalarm.R
import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.presentation.MainActivity
import com.nick.telegramalarm.presentation.alarm.AlarmActivity
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
        .setContentTitle(event.senderName)
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
        .build()

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun alarmPendingIntent(event: AlarmEvent): PendingIntent {
        val intent = AlarmActivity.intent(context, event)
        return PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
