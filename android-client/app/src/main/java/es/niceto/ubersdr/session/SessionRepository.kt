package es.niceto.ubersdr.session

import es.niceto.ubersdr.data.network.ConnectionService
import es.niceto.ubersdr.data.network.dto.BandDto
import es.niceto.ubersdr.data.websocket.AudioWsClient
import es.niceto.ubersdr.data.websocket.SpectrumWsClient
import java.util.UUID

class SessionRepository(
    private val connectionService: ConnectionService,
    private val audioWsClient: AudioWsClient = AudioWsClient(),
    private val spectrumWsClient: SpectrumWsClient = SpectrumWsClient()
) {

    var userSessionId: String = UUID.randomUUID().toString()
        private set

    fun newSessionId(): String {
        userSessionId = UUID.randomUUID().toString()
        return userSessionId
    }

    data class BootstrapResult(
        val allowed: Boolean,
        val reason: String?,
        val sessionId: String,
        val defaultFrequency: Long? = null,
        val defaultMode: String? = null
    )

    suspend fun openConnection(baseUrl: String): BootstrapResult {
        return try {
            val sessionId = newSessionId()

            val response = connectionService.postConnection(
                baseUrl = baseUrl,
                userSessionId = sessionId
            )

            BootstrapResult(
                allowed = response.allowed,
                reason = response.reason,
                sessionId = sessionId
            )

        } catch (e: Exception) {
            BootstrapResult(
                allowed = false,
                reason = e.message,
                sessionId = userSessionId
            )
        }
    }

    suspend fun bootstrapSession(baseUrl: String): BootstrapResult {
        val connectionResult = openConnection(baseUrl)

        if (!connectionResult.allowed) {
            return connectionResult
        }

        return try {
            val description = connectionService.getDescription(baseUrl)

            connectionResult.copy(
                defaultFrequency = description.defaultFrequency,
                defaultMode = description.defaultMode
            )
        } catch (e: Exception) {
            connectionResult.copy(
                allowed = false,
                reason = e.message
            )
        }
    }

    suspend fun getBands(baseUrl: String): List<BandDto> {
        return connectionService.getBands(baseUrl)
    }

    fun connectAudio(
        baseUrl: String,
        frequencyHz: Long,
        mode: String,
        bandwidthLowHz: Int,
        bandwidthHighHz: Int,
        listener: AudioWsClient.Listener
    ) {
        audioWsClient.connect(
            baseUrl = baseUrl,
            userSessionId = userSessionId,
            frequencyHz = frequencyHz,
            mode = mode,
            bandwidthLowHz = bandwidthLowHz,
            bandwidthHighHz = bandwidthHighHz,
            listener = listener
        )
    }

    fun hasActiveAudioConnection(): Boolean {
        return audioWsClient.hasActiveConnection()
    }

    fun sendAudioTune(
        frequencyHz: Long,
        mode: String,
        bandwidthLowHz: Int,
        bandwidthHighHz: Int
    ) {
        audioWsClient.sendTune(
            frequencyHz = frequencyHz,
            mode = mode,
            bandwidthLowHz = bandwidthLowHz,
            bandwidthHighHz = bandwidthHighHz
        )
    }

    fun setAudioVolume(volume: Float) {
        audioWsClient.setVolume(volume)
    }

    fun setAudioMuted(muted: Boolean) {
        audioWsClient.setMuted(muted)
    }

    fun connectSpectrum(
        baseUrl: String,
        listener: SpectrumWsClient.Listener
    ) {
        spectrumWsClient.connect(
            baseUrl = baseUrl,
            userSessionId = userSessionId,
            listener = listener
        )
    }

    fun sendSpectrumZoom(centerFreqHz: Long, binBandwidthHz: Double) {
        spectrumWsClient.sendZoom(
            centerFreqHz = centerFreqHz,
            binBandwidthHz = binBandwidthHz
        )
    }

    fun sendSpectrumPan(centerFreqHz: Long) {
        spectrumWsClient.sendPan(
            centerFreqHz = centerFreqHz
        )
    }

    fun disconnect() {
        audioWsClient.disconnect()
        spectrumWsClient.disconnect()
    }
}
