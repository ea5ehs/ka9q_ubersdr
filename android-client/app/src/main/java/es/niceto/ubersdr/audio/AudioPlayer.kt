package es.niceto.ubersdr.audio

class AudioPlayer(
    private val opusDecoder: OpusDecoder = OpusDecoder()
) {
    fun start() {
        // TODO: Initialize AudioTrack when real audio pipeline is implemented
    }

    fun stop() {
        // TODO: Stop AudioTrack and release resources
    }

    fun feedAudio(packet: ByteArray) {
        // TODO: Parse audio header, decode Opus payload, and send PCM to AudioTrack
    }
}
