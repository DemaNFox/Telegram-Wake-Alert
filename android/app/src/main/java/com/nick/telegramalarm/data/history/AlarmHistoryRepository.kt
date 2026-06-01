package com.nick.telegramalarm.data.history

import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.data.model.AlarmHistoryItem
import kotlinx.coroutines.flow.Flow

interface AlarmHistoryRepository {
    val history: Flow<List<AlarmHistoryItem>>
    suspend fun record(event: AlarmEvent, status: String)
    suspend fun clear()
}
