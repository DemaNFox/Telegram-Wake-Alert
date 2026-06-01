package com.nick.telegramalarm.data.people

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nick.telegramalarm.data.model.TelegramPerson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.peopleCacheDataStore by preferencesDataStore("telegram_alarm_people_cache")

@Singleton
class PeopleCacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PeopleCacheRepository {
    private object Keys {
        val people = stringPreferencesKey("people")
    }

    override val people: Flow<List<TelegramPerson>> = context.peopleCacheDataStore.data.map { prefs ->
        decodePeople(prefs[Keys.people].orEmpty())
    }

    override suspend fun replacePeople(people: List<TelegramPerson>) {
        context.peopleCacheDataStore.edit { prefs ->
            prefs[Keys.people] = encodePeople(people)
        }
    }

    private fun encodePeople(people: List<TelegramPerson>): String =
        JSONArray(
            people.map { person ->
                JSONObject()
                    .put("senderId", person.senderId)
                    .put("name", person.name)
                    .put("username", person.username)
                    .put("lastMessageAt", person.lastMessageAt)
            }
        ).toString()

    private fun decodePeople(raw: String): List<TelegramPerson> {
        val array = runCatching { JSONArray(raw.ifBlank { "[]" }) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val senderId = item.optString("senderId")
                if (senderId.isBlank()) continue
                add(
                    TelegramPerson(
                        senderId = senderId,
                        name = item.optString("name").ifBlank { senderId },
                        username = item.optString("username").takeIf { it.isNotBlank() && it != "null" },
                        lastMessageAt = item.optLong("lastMessageAt").takeIf { it > 0 }
                    )
                )
            }
        }
    }
}
