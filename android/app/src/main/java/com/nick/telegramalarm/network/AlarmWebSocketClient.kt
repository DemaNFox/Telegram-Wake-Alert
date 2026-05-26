package com.nick.telegramalarm.network

import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.data.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow

interface AlarmWebSocketClient {
    val events: Flow<AlarmEvent>
    val status: Flow<ConnectionStatus>
    fun connect(baseUrl: String, token: String, clientId: String)
    fun disconnect()
}
