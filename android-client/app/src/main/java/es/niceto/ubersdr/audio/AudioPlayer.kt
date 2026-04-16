package es.niceto.ubersdr.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioPlayer(
    private val opusDecoder: OpusDecoder = OpusDecoder()
) {
    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int? = null
    private var currentChannels: Int? = null
    private var started = false
    private var volume = 1f
    private var muted = false

    fun start() {
        started = true
        applyVolume()
    }

    fun stop() {
        started = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        currentSampleRate = null
        currentChannels = null
    }

    fun feedAudio(packet: ByteArray) {
        if (!started || packet.size <= OPUS_V2_HEADER_SIZE) {
            return
        }

        val header = ByteBuffer.wrap(packet, 0, OPUS_V2_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.long
        val sampleRate = header.int
        val channels = header.get().toInt() and 0xFF

        if (sampleRate <= 0 || channels <= 0) {
            return
        }

        val opusPayload = packet.copyOfRange(OPUS_V2_HEADER_SIZE, packet.size)
        if (opusPayload.isEmpty()) {
            return
        }

        val pcm = opusDecoder.decode(
            opusData = opusPayload,
            sampleRate = sampleRate,
            channels = channels
        )

        if (pcm.isEmpty()) {
            return
        }

        ensureAudioTrack(sampleRate = sampleRate, channels = channels)
        audioTrack?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
    }

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyVolume()
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        applyVolume()
    }

    private fun ensureAudioTrack(sampleRate: Int, channels: Int) {
        val existingTrack = audioTrack
        if (existingTrack != null && currentSampleRate == sampleRate && currentChannels == channels) {
            return
        }

        existingTrack?.stop()
        existingTrack?.release()

        val channelMask = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> return
        }
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            return
        }

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build(),
            minBufferSize * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also {
            currentSampleRate = sampleRate
            currentChannels = channels
            applyVolume(it)
            it.play()
        }
    }

    private fun applyVolume() {
        applyVolume(audioTrack)
    }

    private fun applyVolume(track: AudioTrack?) {
        track?.setVolume(if (muted) 0f else volume)
    }

    private companion object {
        const val OPUS_V2_HEADER_SIZE = 21
    }
}
