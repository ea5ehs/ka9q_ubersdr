package es.niceto.ubersdr.data.websocket

import es.niceto.ubersdr.model.RadioMode

class AudioWsClient {
    fun connect(baseUrl: String, userSessionId: String) {
        // TODO: Implement OkHttp WebSocket connection to /ws
    }

    fun disconnect() {
        // TODO: Close audio WebSocket
    }

    fun sendTune(
        frequencyHz: Long,
        mode: RadioMode,
        bandwidthLowHz: Int,
        bandwidthHighHz: Int
    ) {
        // TODO: Send complete tune message over audio WebSocket
    }
}
