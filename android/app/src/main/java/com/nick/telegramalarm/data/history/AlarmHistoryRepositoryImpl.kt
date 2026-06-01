package com.nick.telegramalarm.data.history

import android.content.Context
import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.data.model.AlarmHistoryItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmHistoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AlarmHistoryRepository {
    private val prefs = context.getSharedPreferences("alarm_history", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow(load())
    override val history = _history.asStateFlow()

    override suspend fun record(event: AlarmEvent, status: String) {
        val updated = (listOf(
            AlarmHistoryItem(
                senderName = event.senderName,
                message = event.message,
                timestamp = event.timestamp,
                status = status
            )
        ) + _history.value).take(MAX_ITEMS)
        persist(updated)
    }

    override suspend fun clear() {
        persist(emptyList())
    }

    private fun persist(items: List<AlarmHistoryItem>) {
        prefs.edit().putString(KEY_ITEMS, JSONArray(items.map { item ->
            JSONObject()
                .put("senderName", item.senderName)
                .put("message", item.message)
                .put("timestamp", item.timestamp)
                .put("status", item.status)
        }).toString()).apply()
        _history.value = items
    }

    private fun load(): List<AlarmHistoryItem> {
        val raw = prefs.getString(KEY_ITEMS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    AlarmHistoryItem(
                        senderName = item.optString("senderName"),
                        message = item.optString("message"),
                        timestamp = item.optLong("timestamp"),
                        status = item.optString("status")
                    )
                )
            }
        }
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 50
    }
}
