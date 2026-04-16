package es.niceto.ubersdr.data.websocket

import okio.ByteString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class SpectrumWsClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {

    data class SpectrumConfigMessage(
        val rawJson: String,
        val centerFreq: Long?,
        val binCount: Int?,
        val binBandwidthHz: Double?,
        val totalBandwidthHz: Double?,
        val sessionId: String?
    )

    data class SpecFrameInfo(
        val totalSize: Int,
        val payloadSize: Int,
        val flags: Int,
        val reconstructedSize: Int?,
        val matchesBinCount: Boolean,
        val reconstructedData: ByteArray?
    )

    interface Listener {
        fun onConnecting()
        fun onOpen()
        fun onStatusRequested()
        fun onTextMessage(text: String)
        fun onConfig(config: SpectrumConfigMessage)
        fun onSpecFrame(info: SpecFrameInfo)
        fun onPong()
        fun onErrorMessage(message: String)
        fun onFailure(message: String)
        fun onClosed()
    }

    private var webSocket: WebSocket? = null
    private var lastFullSpectrum8: ByteArray? = null
    private var currentBinCount: Int? = null

    fun connect(
        baseUrl: String,
        userSessionId: String,
        listener: Listener
    ) {
        disconnect()

        listener.onConnecting()

        val wsUrl = buildWsUrl(baseUrl, userSessionId)

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen()
                    webSocket.send("""{"type":"get_status"}""")
                    listener.onStatusRequested()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onTextMessage(text)
                    handleJsonText(text, listener)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleBinaryMessage(bytes.toByteArray(), listener)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(t.message ?: "spectrum ws failure")
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
        lastFullSpectrum8 = null
    }

    fun sendZoom(centerFreqHz: Long, binBandwidthHz: Double) {
        webSocket?.send(
            """{"type":"zoom","frequency":$centerFreqHz,"binBandwidth":$binBandwidthHz}"""
        )
    }

    fun sendPan(centerFreqHz: Long) {
        webSocket?.send(
            """{"type":"pan","frequency":$centerFreqHz}"""
        )
    }

    private fun handleBinaryMessage(
        data: ByteArray,
        listener: Listener
    ) {
        if (isSpecFrame(data)) {
            handleSpecFrame(data, listener)
            return
        }

        try {
            val jsonText = ungzipToString(data)
            listener.onTextMessage(jsonText)
            handleJsonText(jsonText, listener)
        } catch (e: Exception) {
            listener.onFailure("invalid spectrum binary message: ${e.message}")
        }
    }

    private fun handleSpecFrame(
        data: ByteArray,
        listener: Listener
    ) {
        if (data.size < 22) {
            return
        }

        val flags = data[5].toInt() and 0xFF
        val payload = data.copyOfRange(22, data.size)
        val binCount = currentBinCount

        when (flags) {
            0x03 -> {
                lastFullSpectrum8 = payload.copyOf()
            }

            0x04 -> {
                val base = lastFullSpectrum8
                if (base == null || binCount == null || base.size != binCount) {
                    return
                }

                if (payload.size < 2) {
                    return
                }

                val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                val changeCount = buffer.short.toInt() and 0xFFFF

                for (i in 0 until changeCount) {
                    if (buffer.remaining() < 3) {
                        break
                    }

                    val index = buffer.short.toInt() and 0xFFFF
                    val value = buffer.get()

                    if (index in base.indices) {
                        base[index] = value
                    }
                }
            }

            else -> {
                return
            }
        }

        val reconstructed = lastFullSpectrum8?.copyOf()
        val reconstructedSize = reconstructed?.size
        val matches = reconstructedSize != null && binCount != null && reconstructedSize == binCount

        listener.onSpecFrame(
            SpecFrameInfo(
                totalSize = data.size,
                payloadSize = payload.size,
                flags = flags,
                reconstructedSize = reconstructedSize,
                matchesBinCount = matches,
                reconstructedData = reconstructed
            )
        )
    }

    private fun handleJsonText(
        text: String,
        listener: Listener
    ) {
        try {
            val json = JSONObject(text)
            val type = findString(json, "type")?.replace(" ", "")

            when (type) {
                "config" -> {
                    val config = SpectrumConfigMessage(
                        rawJson = text,
                        centerFreq = findDouble(json, "centerFreq", "center Freq", "center_frequency", "frequency")
                            ?.roundToLong(),
                        binCount = findDouble(json, "binCount", "bin_count")
                            ?.roundToInt(),
                        binBandwidthHz = findDouble(json, "binBandwidth", "bin_bandwidth"),
                        totalBandwidthHz = findDouble(json, "totalBandwidth", "total_bandwidth"),
                        sessionId = findString(json, "sessionId", "session_id")
                            ?.replace(" ", "")
                    )
                    currentBinCount = config.binCount
                    listener.onConfig(config)
                }

                "pong" -> listener.onPong()

                "error" -> {
                    listener.onErrorMessage(
                        findString(json, "error") ?: "unknown spectrum error"
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun isSpecFrame(data: ByteArray): Boolean {
        return data.size >= 4 &&
            data[0] == 'S'.code.toByte() &&
            data[1] == 'P'.code.toByte() &&
            data[2] == 'E'.code.toByte() &&
            data[3] == 'C'.code.toByte()
    }

    private fun findString(json: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                return json.optString(key)
            }
        }
        return null
    }

    private fun findDouble(json: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (json.has(key) && !json.isNull(key)) {
                val value = json.get(key)
                when (value) {
                    is Number -> return value.toDouble()
                    is String -> {
                        val normalized = value.replace(" ", "")
                        normalized.toDoubleOrNull()?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun ungzipToString(data: ByteArray): String {
        ByteArrayInputStream(data).use { byteStream ->
            GZIPInputStream(byteStream).use { gzipStream ->
                InputStreamReader(gzipStream, Charsets.UTF_8).use { reader ->
                    return reader.readText()
                }
            }
        }
    }

    private fun buildWsUrl(baseUrl: String, userSessionId: String): String {
        val normalized = baseUrl.removeSuffix("/")

        val wsBase = when {
            normalized.startsWith("https://") ->
                "wss://" + normalized.removePrefix("https://")
            normalized.startsWith("http://") ->
                "ws://" + normalized.removePrefix("http://")
            else -> "ws://$normalized"
        }

        return "$wsBase/ws/user-spectrum?user_session_id=$userSessionId&mode=binary8"
    }
}
