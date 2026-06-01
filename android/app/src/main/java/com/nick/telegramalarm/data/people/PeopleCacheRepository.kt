package com.nick.telegramalarm.data.people

import com.nick.telegramalarm.data.model.TelegramPerson
import kotlinx.coroutines.flow.Flow

interface PeopleCacheRepository {
    val people: Flow<List<TelegramPerson>>
    suspend fun replacePeople(people: List<TelegramPerson>)
}
