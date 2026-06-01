package com.nick.telegramalarm.data.backend

import com.nick.telegramalarm.data.model.BackendStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : BackendRepository {
    override suspend fun fetchStatus(backendUrl: String, token: String): BackendStatus {
        val url = httpBase(backendUrl) + "/status?token=${token.urlEncode()}"
        return runCatching {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return BackendStatus(reachable = false, error = "HTTP ${response.code}")
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

    override suspend fun sendTestEvent(backendUrl: String, token: String): Boolean {
        val url = httpBase(backendUrl) + "/test-event?token=${token.urlEncode()}"
        return runCatching {
            okHttpClient.newCall(Request.Builder().url(url).post(okhttp3.RequestBody.create(null, ByteArray(0))).build())
                .execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
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
