package es.niceto.ubersdr.data.websocket

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class AudioWsClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {

    interface Listener {
        fun onConnecting()
        fun onOpen()
        fun onFailure(message: String)
        fun onClosed()
    }

    private var webSocket: WebSocket? = null

    fun connect(
        baseUrl: String,
        userSessionId: String,
        frequencyHz: Long,
        mode: String,
        bandwidthLowHz: Int,
        bandwidthHighHz: Int,
        listener: Listener
    ) {
        disconnect()

        listener.onConnecting()

        val wsUrl = buildWsUrl(
            baseUrl = baseUrl,
            userSessionId = userSessionId,
            frequencyHz = frequencyHz,
            mode = mode,
            bandwidthLowHz = bandwidthLowHz,
            bandwidthHighHz = bandwidthHighHz
        )

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(t.message ?: "unknown websocket failure")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed()
                }
            }
        )
    }

    fun disconnect() {
        webSocket?.close(1000, "client disconnect")
        webSocket = null
    }

    private fun buildWsUrl(
        baseUrl: String,
        userSessionId: String,
        frequencyHz: Long,
        mode: String,
        bandwidthLowHz: Int,
        bandwidthHighHz: Int
    ): String {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) {
            baseUrl.dropLast(1)
        } else {
            baseUrl
        }

        val wsBaseUrl = when {
            normalizedBaseUrl.startsWith("https://") ->
                "wss://" + normalizedBaseUrl.removePrefix("https://")
            normalizedBaseUrl.startsWith("http://") ->
                "ws://" + normalizedBaseUrl.removePrefix("http://")
            else ->
                "ws://$normalizedBaseUrl"
        }

        return "$wsBaseUrl/ws" +
            "?frequency=$frequencyHz" +
            "&mode=$mode" +
            "&user_session_id=$userSessionId" +
            "&format=opus" +
            "&version=2" +
            "&bandwidthLow=$bandwidthLowHz" +
            "&bandwidthHigh=$bandwidthHighHz"
    }
}
