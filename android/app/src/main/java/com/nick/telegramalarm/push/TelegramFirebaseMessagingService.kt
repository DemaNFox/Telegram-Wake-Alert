package com.nick.telegramalarm.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.service.AlarmForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TelegramFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushRegistrationManager: PushRegistrationManager

    override fun onRegistered(installationId: String) {
        pushRegistrationManager.register(installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "new_message") return
        val event = AlarmEvent(
            chatId = data["chat_id"].orEmpty(),
            senderId = data["sender_id"].orEmpty(),
            senderName = data["sender_name"].orEmpty(),
            message = data["message"].orEmpty(),
            timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis() / 1_000,
            chatTitle = data["chat_title"]?.takeIf { it.isNotBlank() },
            reason = data["reason"].orEmpty().ifBlank { "private_user" },
            eventId = data["event_id"].orEmpty()
        )
        AlarmForegroundService.pushAlarm(this, event)
    }
}
