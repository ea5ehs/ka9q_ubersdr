package es.niceto.ubersdr.presentation.radio

import es.niceto.ubersdr.data.network.dto.BandDto
import es.niceto.ubersdr.model.RadioMode

data class RadioUiState(
    val frequencyHz: Long = 14_175_000L,
    val mode: RadioMode = RadioMode.USB,
    val bandwidthLowHz: Int = 150,
    val bandwidthHighHz: Int = 2700,
    val isConnected: Boolean = false,
    val statusText: String = "Idle",
    val audioVolume: Float = 1f,
    val audioMuted: Boolean = false,
    val tuningStepHz: Long = 1_000L,
    val keepScreenOn: Boolean = false,
    val spectrumCenterFreqHz: Long? = null,
    val spectrumBinCount: Int? = null,
    val spectrumBinBandwidthHz: Double? = null,
    val spectrumTotalBandwidthHz: Double? = null,
    val lastSpecFrameSize: Int? = null,
    val lastSpecPayloadSize: Int? = null,
    val specFramesReceived: Long = 0L,
    val specLastFlags: Int? = null,
    val specBufferSize: Int? = null,
    val specBufferMatchesBinCount: Boolean = false,
    val latestSpectrumRow: ByteArray? = null,
    val availableBands: List<BandDto> = emptyList()
)
