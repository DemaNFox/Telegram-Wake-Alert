package com.nick.telegramalarm.data.backend

import com.nick.telegramalarm.data.model.BackendActionResult
import com.nick.telegramalarm.data.model.BackendStatus
import com.nick.telegramalarm.data.model.PeopleFetchResult
import com.nick.telegramalarm.data.model.GroupsFetchResult

interface BackendRepository {
    suspend fun fetchStatus(backendUrl: String, token: String): BackendStatus
    suspend fun sendTestEvent(backendUrl: String, token: String): BackendActionResult
    suspend fun fetchRecentPeople(backendUrl: String, token: String): PeopleFetchResult
    suspend fun fetchRecentGroups(backendUrl: String, token: String): GroupsFetchResult
}
