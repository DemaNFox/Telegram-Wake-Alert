package com.nick.telegramalarm.data.model

data class AlarmEvent(
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val chatTitle: String? = null,
    val reason: String = "private_user",
    val eventId: String = ""
)

data class AlarmHistoryItem(
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val status: String,
    val chatId: String = "",
    val chatTitle: String? = null,
    val senderId: String = "",
    val eventId: String = ""
)
