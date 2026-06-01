package com.nick.telegramalarm.data.backend

import com.nick.telegramalarm.data.model.BackendActionResult
import com.nick.telegramalarm.data.model.BackendStatus
import com.nick.telegramalarm.data.model.PeopleFetchResult

interface BackendRepository {
    suspend fun fetchStatus(backendUrl: String, token: String): BackendStatus
    suspend fun sendTestEvent(backendUrl: String, token: String): BackendActionResult
    suspend fun fetchRecentPeople(backendUrl: String, token: String): PeopleFetchResult
}
