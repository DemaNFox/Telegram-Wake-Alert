package com.nick.telegramalarm.data.backend

import com.nick.telegramalarm.data.model.BackendStatus
import com.nick.telegramalarm.data.model.TelegramPerson

interface BackendRepository {
    suspend fun fetchStatus(backendUrl: String, token: String): BackendStatus
    suspend fun sendTestEvent(backendUrl: String, token: String): Boolean
    suspend fun fetchRecentPeople(backendUrl: String, token: String): List<TelegramPerson>
}
