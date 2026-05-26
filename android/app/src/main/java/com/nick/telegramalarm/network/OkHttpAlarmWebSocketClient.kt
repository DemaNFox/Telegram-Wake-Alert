package com.nick.telegramalarm.network

import android.util.Log
import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.data.model.ConnectionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpAlarmWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) : AlarmWebSocketClient {
    private val _events = MutableSharedFlow<AlarmEvent>(extraBufferCapacity = 64)
    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private var socket: WebSocket? = null

    override val events = _events.asSharedFlow()
    override val status = _status.asStateFlow()

    override fun connect(baseUrl: String, token: String, clientId: String) {
        disconnect()
        val url = buildWebSocketUrl(baseUrl, token, clientId) ?: run {
                _status.value = ConnectionStatus.FAILED
                return
            }
        _status.value = ConnectionStatus.CONNECTING
        socket = okHttpClient.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    override fun disconnect() {
        socket?.close(1000, "client_disconnect")
        socket = null
        _status.value = ConnectionStatus.DISCONNECTED
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _status.value = ConnectionStatus.CONNECTED
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (json.optString("type")) {
                "heartbeat" -> webSocket.send("""{"type":"pong"}""")
                "new_message" -> _events.tryEmit(
                    AlarmEvent(
                        chatId = json.optString("chat_id"),
                        senderId = json.optString("sender_id"),
                        senderName = json.optString("sender_name"),
                        message = json.optString("message"),
                        timestamp = json.optLong("timestamp")
                    )
                )
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w("AlarmWebSocket", "WebSocket failure", t)
            _status.value = ConnectionStatus.FAILED
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _status.value = ConnectionStatus.DISCONNECTED
        }
    }

    private fun buildWebSocketUrl(baseUrl: String, token: String, clientId: String): String? {
        val trimmed = baseUrl.trim()
        if (!trimmed.startsWith("ws://") && !trimmed.startsWith("wss://")) return null
        val separator = if (trimmed.contains("?")) "&" else "?"
        return trimmed +
            separator +
            "token=${token.urlEncode()}&client_id=${clientId.urlEncode()}"
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}
