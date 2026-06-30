package com.nick.telegramalarm.data.groups

import com.nick.telegramalarm.data.model.TelegramGroup
import kotlinx.coroutines.flow.Flow

interface GroupsCacheRepository {
    val groups: Flow<List<TelegramGroup>>
    suspend fun replaceGroups(groups: List<TelegramGroup>)
}
