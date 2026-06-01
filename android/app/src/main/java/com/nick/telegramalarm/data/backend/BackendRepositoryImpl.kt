package com.nick.telegramalarm.data.backend

import com.nick.telegramalarm.data.model.BackendActionResult
import com.nick.telegramalarm.data.model.BackendStatus
import com.nick.telegramalarm.data.model.PeopleFetchResult
import com.nick.telegramalarm.data.model.TelegramPerson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : BackendRepository {
    override suspend fun fetchStatus(backendUrl: String, token: String): BackendStatus = withContext(Dispatchers.IO) {
        val url = httpBase(backendUrl) + "/status?token=${token.urlEncode()}"
        runCatching {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext BackendStatus(reachable = false, error = "HTTP ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                val websocket = json.optJSONObject("websocket")
                val telegram = json.optJSONObject("telegram")
                BackendStatus(
                    reachable = true,
                    websocketClients = websocket?.optInt("clients_count"),
                    telegramConnected = telegram?.optBoolean("connected"),
                    lastTelegramMessageAt = telegram?.optLong("last_message_at")?.takeIf { it > 0 }
                )
            }
        }.getOrElse { BackendStatus(reachable = false, error = it.message) }
    }

    override suspend fun sendTestEvent(backendUrl: String, token: String): BackendActionResult = withContext(Dispatchers.IO) {
        val url = httpBase(backendUrl) + "/test-event?token=${token.urlEncode()}"
        runCatching {
            okHttpClient.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody(null)).build())
                .execute()
                .use {
                    if (it.isSuccessful) BackendActionResult(true, "Backend test sent")
                    else BackendActionResult(false, "Backend test failed: HTTP ${it.code}")
                }
        }.getOrElse { BackendActionResult(false, "Backend test failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    override suspend fun fetchRecentPeople(backendUrl: String, token: String): PeopleFetchResult = withContext(Dispatchers.IO) {
        val url = httpBase(backendUrl) + "/people/recent?token=${token.urlEncode()}&limit=50"
        runCatching {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext PeopleFetchResult(message = "People load failed: HTTP ${response.code}")
                val array = JSONObject(response.body?.string().orEmpty()).optJSONArray("people")
                    ?: return@withContext PeopleFetchResult(message = "People load failed: invalid response")
                val people = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(
                            TelegramPerson(
                                senderId = item.optString("sender_id"),
                                name = item.optString("name"),
                                username = item.optString("username").takeIf { it.isNotBlank() && it != "null" },
                                lastMessageAt = item.optLong("last_message_at").takeIf { it > 0 }
                            )
                        )
                    }
                }
                PeopleFetchResult(people = people, message = "Loaded ${people.size} people", success = true)
            }
        }.getOrElse { PeopleFetchResult(message = "People load failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun httpBase(backendUrl: String): String {
        val trimmed = backendUrl.trim().removeSuffix("/ws")
        return trimmed
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .substringBefore("?")
            .removeSuffix("/")
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}
