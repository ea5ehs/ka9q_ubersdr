package es.niceto.ubersdr.data.websocket

class SpectrumWsClient {
    fun connect(baseUrl: String, userSessionId: String) {
        // TODO: Implement OkHttp WebSocket connection to /ws/user-spectrum
    }

    fun disconnect() {
        // TODO: Close spectrum WebSocket
    }

    fun sendTune(centerFrequencyHz: Long) {
        // TODO: Send pan/zoom/reset messages when spectrum interaction is implemented
    }
}
