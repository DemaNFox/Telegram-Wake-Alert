package com.nick.telegramalarm.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.data.model.AppSettings
import com.nick.telegramalarm.data.model.ConnectionStatus
import com.nick.telegramalarm.data.history.AlarmHistoryRepository
import com.nick.telegramalarm.data.settings.SettingsRepository
import com.nick.telegramalarm.domain.AlarmController
import com.nick.telegramalarm.network.AlarmWebSocketClient
import com.nick.telegramalarm.notifications.NotificationFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class AlarmForegroundService : Service() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var webSocketClient: AlarmWebSocketClient
    @Inject lateinit var alarmController: AlarmController
    @Inject lateinit var notificationFactory: NotificationFactory
    @Inject lateinit var alarmHistoryRepository: AlarmHistoryRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clientId = UUID.randomUUID().toString()
    private var started = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notificationFactory.foreground("Starting"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ServiceActions.STOP_ALARM -> alarmController.stop()
            ServiceActions.MUTE_ONE_MINUTE -> alarmController.muteOneMinute()
            ServiceActions.SNOOZE_FIVE_MINUTES -> alarmController.snooze(5)
            ServiceActions.SNOOZE_TEN_MINUTES -> alarmController.snooze(10)
            ServiceActions.TEST_ALARM -> scope.launch {
                val settings = settingsRepository.settings.first()
                val soundUri = if (settings.useDefaultAlarmSound) null else settings.customAlarmSoundUri
                alarmController.trigger(testEvent(), settings.volume, soundUri, settings.volumeRampEnabled)
            }
            else -> startCollectorsOnce()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        webSocketClient.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun startCollectorsOnce() {
        if (started) return
        started = true
        scope.launch {
            settingsRepository.settings
                .map { ConnectionConfig(it.backendUrl, it.authToken, it.serviceEnabled) }
                .distinctUntilChanged()
                .collectLatest { config ->
                    if (config.serviceEnabled && config.authToken.isNotBlank()) {
                        webSocketClient.connect(config.backendUrl, config.authToken, clientId)
                    } else {
                        webSocketClient.disconnect()
                    }
                }
        }
        scope.launch {
            webSocketClient.events.collect { event ->
                val settings = settingsRepository.settings.first()
                if (settings.alertsEnabled) {
                    if (!isEventSourceEnabled(event, settings)) {
                        alarmHistoryRepository.record(event, "source_filter")
                    } else if (!isSenderAllowed(event, settings)) {
                        alarmHistoryRepository.record(event, "people_filter")
                    } else if (isQuietNow(settings)) {
                        alarmHistoryRepository.record(event, "quiet_hours")
                    } else {
                        alarmHistoryRepository.record(event, "played")
                        val soundUri = if (settings.useDefaultAlarmSound) null else settings.customAlarmSoundUri
                        alarmController.trigger(event, settings.volume, soundUri, settings.volumeRampEnabled)
                        if (settings.alarmDurationSeconds > 0) {
                            launch {
                                delay(settings.alarmDurationSeconds * 1000L)
                                alarmController.stop()
                            }
                        }
                    }
                } else {
                    alarmHistoryRepository.record(event, "alerts_disabled")
                }
            }
        }
        scope.launch {
            webSocketClient.status.collect { status ->
                startForeground(NOTIFICATION_ID, notificationFactory.foreground(status.name.lowercase()))
                if (status == ConnectionStatus.CONNECTED) {
                    NotificationManagerCompat.from(this@AlarmForegroundService).cancel(CONNECTION_LOST_NOTIFICATION_ID)
                }
            }
        }
        scope.launch {
            connectionLostMonitor()
        }
        scope.launch {
            reconnectLoop()
        }
    }

    private suspend fun connectionLostMonitor() {
        var disconnectedSince: Long? = null
        while (true) {
            val settings = settingsRepository.settings.first()
            val status = webSocketClient.status.first()
            if (settings.serviceEnabled && settings.authToken.isNotBlank() && status != ConnectionStatus.CONNECTED) {
                val now = System.currentTimeMillis()
                disconnectedSince = disconnectedSince ?: now
                val downForMs = now - disconnectedSince
                if (downForMs >= CONNECTION_LOST_THRESHOLD_MS) {
                    NotificationManagerCompat.from(this).notify(
                        CONNECTION_LOST_NOTIFICATION_ID,
                        notificationFactory.connectionLost(downForMs / 60_000)
                    )
                }
            } else {
                disconnectedSince = null
            }
            delay(30_000L)
        }
    }

    private suspend fun reconnectLoop() {
        var delayMs = 2_000L
        while (true) {
            val settings = settingsRepository.settings.first()
            val status = webSocketClient.status.first()
            if (settings.serviceEnabled && settings.autoReconnect && settings.authToken.isNotBlank() &&
                (status == ConnectionStatus.FAILED || status == ConnectionStatus.DISCONNECTED)
            ) {
                webSocketClient.connect(settings.backendUrl, settings.authToken, clientId)
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(60_000L)
            } else {
                delayMs = 2_000L
                delay(2_000L)
            }
        }
    }

    private fun testEvent() = AlarmEvent(
        chatId = "test",
        senderId = "test",
        senderName = "Test Alarm",
        message = "This is a local alarm test.",
        timestamp = System.currentTimeMillis() / 1000
    )

    private fun isQuietNow(settings: AppSettings): Boolean {
        if (!settings.quietHoursEnabled) return false
        val now = java.time.LocalTime.now()
        val start = runCatching { java.time.LocalTime.parse(settings.quietHoursStart) }.getOrNull() ?: return false
        val end = runCatching { java.time.LocalTime.parse(settings.quietHoursEnd) }.getOrNull() ?: return false
        return if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }

    private fun isSenderAllowed(event: AlarmEvent, settings: AppSettings): Boolean {
        val senderId = event.senderId.trim()
        val blocked = parseSenderIds(settings.blockedSenderIds)
        if (senderId in blocked) return false
        val allowed = parseSenderIds(settings.allowedSenderIds)
        return allowed.isEmpty() || senderId in allowed
    }

    private fun isEventSourceEnabled(event: AlarmEvent, settings: AppSettings): Boolean =
        when (event.reason) {
            "private_user" -> settings.alertPrivateUsers
            "private_bot" -> settings.alertPrivateBots
            "group_mention" -> settings.alertGroupMentions
            "group_reply" -> settings.alertGroupReplies
            else -> false
        }

    private fun parseSenderIds(value: String): Set<String> =
        value.split(",", "\n", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CONNECTION_LOST_NOTIFICATION_ID = 1002
        private const val CONNECTION_LOST_THRESHOLD_MS = 60_000L

        fun start(context: android.content.Context) {
            val intent = Intent(context, AlarmForegroundService::class.java).setAction(ServiceActions.START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun action(context: android.content.Context, action: String) {
            val intent = Intent(context, AlarmForegroundService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private data class ConnectionConfig(
        val backendUrl: String,
        val authToken: String,
        val serviceEnabled: Boolean
    )
}
