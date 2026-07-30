package com.nick.telegramalarm.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
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
import kotlinx.coroutines.Job
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
    private var alarmTimeoutJob: Job? = null
    private var alarmActive = false
    private var currentConnectionStatus = ConnectionStatus.DISCONNECTED
    private val recentEventIds = linkedMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        promoteRemoteMessaging("Starting")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ServiceActions.STOP_ALARM -> stopCurrentAlarm()
            ServiceActions.MUTE_ONE_MINUTE -> snoozeCurrentAlarm(1)
            ServiceActions.SNOOZE_FIVE_MINUTES -> snoozeCurrentAlarm(5)
            ServiceActions.SNOOZE_TEN_MINUTES -> snoozeCurrentAlarm(10)
            ServiceActions.TEST_ALARM -> scope.launch {
                val settings = settingsRepository.settings.first()
                val soundUri = if (settings.useDefaultAlarmSound) null else settings.customAlarmSoundUri
                val event = testEvent()
                if (alarmController.trigger(event, settings.volume, soundUri, settings.volumeRampEnabled)) {
                    alarmActive = true
                    promoteAlarm(event)
                    scheduleAlarmStop(settings.alarmDurationSeconds)
                }
            }
            ServiceActions.PUSH_ALARM -> intent.toAlarmEvent()?.let { event ->
                scope.launch { handleIncomingEvent(event, "played_push") }
            }
            else -> startCollectorsOnce()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCurrentAlarm(updateForeground = false)
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
                handleIncomingEvent(event, "played_websocket")
            }
        }
        scope.launch {
            webSocketClient.status.collect { status ->
                currentConnectionStatus = status
                if (!alarmActive) {
                    promoteRemoteMessaging(status.name.lowercase())
                }
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
                if (downForMs >= CONNECTION_LOST_THRESHOLD_MS && canPostNotifications()) {
                    postConnectionLostNotification(downForMs / 60_000)
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

    private suspend fun handleIncomingEvent(event: AlarmEvent, historyStatus: String) {
        val settings = settingsRepository.settings.first()
        if (!settings.alertsEnabled) return
        if (isChatBlocked(event, settings)) return
        if (!isEventSourceEnabled(event, settings)) return
        if (!isSelectedGroupEvent(event, settings) && !isSenderAllowed(event, settings)) return
        if (isQuietNow(settings)) return
        if (isDuplicate(event)) return

        val soundUri = if (settings.useDefaultAlarmSound) null else settings.customAlarmSoundUri
        if (!alarmController.trigger(event, settings.volume, soundUri, settings.volumeRampEnabled)) return
        alarmActive = true
        promoteAlarm(event)
        scheduleAlarmStop(settings.alarmDurationSeconds)
        alarmHistoryRepository.record(event, historyStatus)
    }

    private fun isDuplicate(event: AlarmEvent): Boolean {
        val now = System.currentTimeMillis()
        recentEventIds.entries.removeAll { now - it.value > EVENT_DEDUPLICATION_WINDOW_MS }
        val key = event.eventId.ifBlank {
            listOf(event.chatId, event.senderId, event.timestamp.toString(), event.message)
                .joinToString("\u0000")
        }
        if (key in recentEventIds) return true
        recentEventIds[key] = now
        return false
    }

    private fun isChatBlocked(event: AlarmEvent, settings: AppSettings): Boolean =
        event.chatId.trim() in parseSenderIds(settings.blockedChatIds)

    private fun isEventSourceEnabled(event: AlarmEvent, settings: AppSettings): Boolean =
        if (isSelectedGroupEvent(event, settings)) {
            true
        } else {
        when (event.reason) {
            "private_user" -> settings.alertPrivateUsers
            "private_bot" -> settings.alertPrivateBots
            "group_mention" -> settings.alertGroupMentions
            "group_reply" -> settings.alertGroupReplies
            else -> false
        }
        }

    private fun isSelectedGroupEvent(event: AlarmEvent, settings: AppSettings): Boolean =
        settings.selectedGroupsEnabled &&
            event.reason.startsWith("group_") &&
            event.chatId in parseSenderIds(settings.selectedGroupIds)

    private fun parseSenderIds(value: String): Set<String> =
        value.split(",", "\n", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun postConnectionLostNotification(minutes: Long) {
        NotificationManagerCompat.from(this).notify(
            CONNECTION_LOST_NOTIFICATION_ID,
            notificationFactory.connectionLost(minutes)
        )
    }

    private fun stopCurrentAlarm(updateForeground: Boolean = true) {
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = null
        alarmActive = false
        alarmController.stop()
        if (updateForeground) {
            promoteRemoteMessaging(currentConnectionStatus.name.lowercase())
        }
    }

    private fun snoozeCurrentAlarm(minutes: Int) {
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = null
        alarmActive = false
        alarmController.snooze(minutes)
        promoteRemoteMessaging(currentConnectionStatus.name.lowercase())
    }

    private fun scheduleAlarmStop(durationSeconds: Int) {
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = if (durationSeconds > 0) {
            scope.launch {
                delay(durationSeconds * 1000L)
                alarmController.stop()
                alarmTimeoutJob = null
                alarmActive = false
                promoteRemoteMessaging(currentConnectionStatus.name.lowercase())
            }
        } else {
            null
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CONNECTION_LOST_NOTIFICATION_ID = 1002
        private const val CONNECTION_LOST_THRESHOLD_MS = 60_000L
        private const val EVENT_DEDUPLICATION_WINDOW_MS = 5 * 60_000L
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val EXTRA_SENDER_ID = "sender_id"
        private const val EXTRA_SENDER_NAME = "sender_name"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_TIMESTAMP = "timestamp"
        private const val EXTRA_CHAT_TITLE = "chat_title"
        private const val EXTRA_REASON = "reason"

        fun start(context: android.content.Context) {
            val intent = Intent(context, AlarmForegroundService::class.java).setAction(ServiceActions.START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun action(context: android.content.Context, action: String) {
            val intent = Intent(context, AlarmForegroundService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pushAlarm(context: android.content.Context, event: AlarmEvent) {
            val intent = Intent(context, AlarmForegroundService::class.java)
                .setAction(ServiceActions.PUSH_ALARM)
                .putExtra(EXTRA_EVENT_ID, event.eventId)
                .putExtra(EXTRA_CHAT_ID, event.chatId)
                .putExtra(EXTRA_SENDER_ID, event.senderId)
                .putExtra(EXTRA_SENDER_NAME, event.senderName)
                .putExtra(EXTRA_MESSAGE, event.message)
                .putExtra(EXTRA_TIMESTAMP, event.timestamp)
                .putExtra(EXTRA_CHAT_TITLE, event.chatTitle)
                .putExtra(EXTRA_REASON, event.reason)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun promoteRemoteMessaging(status: String) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notificationFactory.foreground(status),
            serviceType
        )
    }

    private fun promoteAlarm(event: AlarmEvent) {
        val remoteMessagingType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notificationFactory.foreground("Alarm: ${event.senderName}"),
            remoteMessagingType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun Intent.toAlarmEvent(): AlarmEvent? {
        val chatId = getStringExtra(EXTRA_CHAT_ID).orEmpty()
        val senderId = getStringExtra(EXTRA_SENDER_ID).orEmpty()
        if (chatId.isBlank() || senderId.isBlank()) return null
        return AlarmEvent(
            chatId = chatId,
            senderId = senderId,
            senderName = getStringExtra(EXTRA_SENDER_NAME).orEmpty(),
            message = getStringExtra(EXTRA_MESSAGE).orEmpty(),
            timestamp = getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis() / 1_000),
            chatTitle = getStringExtra(EXTRA_CHAT_TITLE)?.takeIf { it.isNotBlank() },
            reason = getStringExtra(EXTRA_REASON).orEmpty().ifBlank { "private_user" },
            eventId = getStringExtra(EXTRA_EVENT_ID).orEmpty()
        )
    }

    private data class ConnectionConfig(
        val backendUrl: String,
        val authToken: String,
        val serviceEnabled: Boolean
    )
}
