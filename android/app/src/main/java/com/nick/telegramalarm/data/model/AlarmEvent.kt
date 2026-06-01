package com.nick.telegramalarm.data.model

data class AlarmEvent(
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val timestamp: Long
)

data class AlarmHistoryItem(
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val status: String
)
