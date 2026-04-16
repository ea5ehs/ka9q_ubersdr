package es.niceto.ubersdr.audio

import android.util.Log
import io.github.jaredmdobson.concentus.OpusDecoder as ConcentusOpusDecoder
import io.github.jaredmdobson.concentus.OpusException

class OpusDecoder {
    private companion object {
        const val TAG = "UberSDR-OpusDecoder"
    }

    private var decoder: ConcentusOpusDecoder? = null
    private var currentSampleRate: Int? = null
    private var currentChannels: Int? = null

    fun decode(
        opusData: ByteArray,
        sampleRate: Int,
        channels: Int
    ): ShortArray {
        if (sampleRate <= 0 || channels <= 0 || opusData.isEmpty()) {
            return ShortArray(0)
        }

        val decoderInstance = getOrCreateDecoder(
            sampleRate = sampleRate,
            channels = channels
        ) ?: return ShortArray(0)

        val maxFrameSize = sampleRate / 10
        val pcmBuffer = ShortArray(maxFrameSize * channels)

        return try {
            val decodedSamplesPerChannel = decoderInstance.decode(
                opusData,
                0,
                opusData.size,
                pcmBuffer,
                0,
                maxFrameSize,
                false
            )

            if (decodedSamplesPerChannel <= 0) {
                Log.d(TAG, "decode() decodedSamplesPerChannel=$decodedSamplesPerChannel")
                ShortArray(0)
            } else {
                pcmBuffer.copyOf(decodedSamplesPerChannel * channels)
            }
        } catch (e: OpusException) {
            Log.w(TAG, "decode() failed: ${e.message}")
            ShortArray(0)
        }
    }

    private fun getOrCreateDecoder(
        sampleRate: Int,
        channels: Int
    ): ConcentusOpusDecoder? {
        val existingDecoder = decoder
        if (
            existingDecoder != null &&
            currentSampleRate == sampleRate &&
            currentChannels == channels
        ) {
            return existingDecoder
        }

        return try {
            ConcentusOpusDecoder(sampleRate, channels).also { newDecoder ->
                Log.d(TAG, "getOrCreateDecoder() new decoder sampleRate=$sampleRate channels=$channels")
                decoder = newDecoder
                currentSampleRate = sampleRate
                currentChannels = channels
            }
        } catch (e: OpusException) {
            Log.w(TAG, "getOrCreateDecoder() failed: ${e.message}")
            null
        }
    }
}
