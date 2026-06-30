package com.nick.telegramalarm.data.groups

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nick.telegramalarm.data.model.TelegramGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.groupsCacheDataStore by preferencesDataStore("telegram_alarm_groups_cache")

@Singleton
class GroupsCacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : GroupsCacheRepository {
    private object Keys {
        val groups = stringPreferencesKey("groups")
    }

    override val groups: Flow<List<TelegramGroup>> = context.groupsCacheDataStore.data.map { prefs ->
        decodeGroups(prefs[Keys.groups].orEmpty())
    }

    override suspend fun replaceGroups(groups: List<TelegramGroup>) {
        context.groupsCacheDataStore.edit { prefs ->
            prefs[Keys.groups] = JSONArray(
                groups.map { group ->
                    JSONObject()
                        .put("chatId", group.chatId)
                        .put("title", group.title)
                        .put("lastMessageAt", group.lastMessageAt)
                }
            ).toString()
        }
    }

    private fun decodeGroups(raw: String): List<TelegramGroup> {
        val array = runCatching { JSONArray(raw.ifBlank { "[]" }) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val chatId = item.optString("chatId")
                if (chatId.isBlank()) continue
                add(
                    TelegramGroup(
                        chatId = chatId,
                        title = item.optString("title").ifBlank { chatId },
                        lastMessageAt = item.optLong("lastMessageAt").takeIf { it > 0 }
                    )
                )
            }
        }
    }
}
