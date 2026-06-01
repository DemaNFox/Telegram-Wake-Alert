package com.nick.telegramalarm.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.nick.telegramalarm.data.model.AlarmEvent
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
            ServiceActions.TEST_ALARM -> scope.launch { alarmController.trigger(testEvent(), settingsRepository.settings.first().volume) }
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
                    alarmHistoryRepository.record(event, "played")
                    alarmController.trigger(event, settings.volume)
                } else {
                    alarmHistoryRepository.record(event, "alerts_disabled")
                }
            }
        }
        scope.launch {
            webSocketClient.status.collect { status ->
                startForeground(NOTIFICATION_ID, notificationFactory.foreground(status.name.lowercase()))
            }
        }
        scope.launch {
            reconnectLoop()
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

    companion object {
        private const val NOTIFICATION_ID = 1001

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
