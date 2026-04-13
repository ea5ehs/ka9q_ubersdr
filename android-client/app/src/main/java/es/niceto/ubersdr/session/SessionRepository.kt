package es.niceto.ubersdr.session

import es.niceto.ubersdr.data.websocket.AudioWsClient
import es.niceto.ubersdr.data.websocket.SpectrumWsClient
import java.util.UUID

class SessionRepository(
    private val audioWsClient: AudioWsClient = AudioWsClient(),
    private val spectrumWsClient: SpectrumWsClient = SpectrumWsClient()
) {
    var userSessionId: String = UUID.randomUUID().toString()
        private set

    fun newSessionId(): String {
        userSessionId = UUID.randomUUID().toString()
        return userSessionId
    }

    fun connect(baseUrl: String) {
        audioWsClient.connect(baseUrl = baseUrl, userSessionId = userSessionId)
        spectrumWsClient.connect(baseUrl = baseUrl, userSessionId = userSessionId)
    }

    fun disconnect() {
        audioWsClient.disconnect()
        spectrumWsClient.disconnect()
    }
}
