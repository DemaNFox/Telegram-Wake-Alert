package com.nick.telegramalarm.data.backend

import com.nick.telegramalarm.data.model.BackendStatus

interface BackendRepository {
    suspend fun fetchStatus(backendUrl: String, token: String): BackendStatus
    suspend fun sendTestEvent(backendUrl: String, token: String): Boolean
}
