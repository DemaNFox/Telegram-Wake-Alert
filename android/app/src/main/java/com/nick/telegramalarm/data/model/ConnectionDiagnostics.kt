package com.nick.telegramalarm.data.model

data class ConnectionDiagnostics(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val lastConnectedAt: Long? = null,
    val lastDisconnectedAt: Long? = null,
    val lastFailureReason: String? = null,
    val lastHeartbeatAt: Long? = null,
    val lastEventAt: Long? = null,
    val reconnectAttempts: Int = 0
)

data class BackendStatus(
    val reachable: Boolean,
    val websocketClients: Int? = null,
    val telegramConnected: Boolean? = null,
    val lastTelegramMessageAt: Long? = null,
    val error: String? = null
)

data class TelegramPerson(
    val senderId: String,
    val name: String,
    val username: String?,
    val lastMessageAt: Long?
)

data class PeopleFetchResult(
    val people: List<TelegramPerson> = emptyList(),
    val message: String,
    val success: Boolean = false
)

data class BackendActionResult(
    val success: Boolean,
    val message: String
)
