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
