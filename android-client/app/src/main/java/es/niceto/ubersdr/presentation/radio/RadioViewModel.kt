package es.niceto.ubersdr.presentation.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.niceto.ubersdr.data.network.dto.BandDto
import es.niceto.ubersdr.data.websocket.AudioWsClient
import es.niceto.ubersdr.data.websocket.SpectrumWsClient
import es.niceto.ubersdr.model.RadioMode
import es.niceto.ubersdr.session.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RadioViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private data class ModeBandwidth(
        val lowHz: Int,
        val highHz: Int
    )

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()
    private var maxObservedSpectrumBinBandwidthHz: Double? = null

    private val baseUrl = "https://ubersdr.niceto.es/"

    init {
        loadBands()
    }

    private val audioListener = object : AudioWsClient.Listener {
        override fun onConnecting() {
            _uiState.value = _uiState.value.copy(
                statusText = "AUDIO WS CONNECTING"
            )
        }

        override fun onOpen() {
            _uiState.value = _uiState.value.copy(
                statusText = "AUDIO WS OPEN"
            )

            viewModelScope.launch {
                delay(1500)

                sessionRepository.connectSpectrum(
                    baseUrl = baseUrl,
                    listener = object : SpectrumWsClient.Listener {
                        @Volatile
                        var gotAnySpectrumMessage = false

                        override fun onConnecting() {
                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM WS CONNECTING"
                            )
                        }

                        override fun onOpen() {
                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM WS OPEN"
                            )

                            viewModelScope.launch {
                                delay(2000)
                                if (!gotAnySpectrumMessage) {
                                    _uiState.value = _uiState.value.copy(
                                        statusText = "SPECTRUM NO RESPONSE"
                                    )
                                }
                            }
                        }

                        override fun onStatusRequested() {
                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM GET_STATUS SENT"
                            )
                        }

                        override fun onTextMessage(text: String) {
                            gotAnySpectrumMessage = true
                            val trimmed = text.replace("\n", " ").take(180)
                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM TEXT: $trimmed"
                            )
                        }

                        override fun onConfig(config: SpectrumWsClient.SpectrumConfigMessage) {
                            gotAnySpectrumMessage = true
                            val validBinBandwidthHz = config.binBandwidthHz
                                ?.takeIf { it.isFinite() && it > 0.0 }
                            if (validBinBandwidthHz != null) {
                                maxObservedSpectrumBinBandwidthHz = maxOf(
                                    maxObservedSpectrumBinBandwidthHz ?: validBinBandwidthHz,
                                    validBinBandwidthHz
                                )
                            }

                            val cf = config.centerFreq?.toString() ?: "null"
                            val bins = config.binCount?.toString() ?: "null"
                            val binBw = config.binBandwidthHz?.toString() ?: "null"
                            val totalBw = config.totalBandwidthHz?.toString() ?: "null"

                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM CONFIG OK cf=$cf bins=$bins binBw=$binBw totalBw=$totalBw",
                                spectrumCenterFreqHz = config.centerFreq,
                                spectrumBinCount = config.binCount,
                                spectrumBinBandwidthHz = config.binBandwidthHz,
                                spectrumTotalBandwidthHz = config.totalBandwidthHz
                            )
                        }

                        override fun onSpecFrame(info: SpectrumWsClient.SpecFrameInfo) {
                            gotAnySpectrumMessage = true

                            _uiState.value = _uiState.value.copy(
                                statusText = "SPEC flags=${info.flags} total=${info.totalSize} payload=${info.payloadSize} reconstructed=${info.reconstructedSize} match=${info.matchesBinCount}",
                                lastSpecFrameSize = info.totalSize,
                                lastSpecPayloadSize = info.payloadSize,
                                specFramesReceived = _uiState.value.specFramesReceived + 1,
                                specLastFlags = info.flags,
                                specBufferSize = info.reconstructedSize,
                                specBufferMatchesBinCount = info.matchesBinCount,
                                latestSpectrumRow = info.reconstructedData
                            )
                        }

                        override fun onPong() {
                            gotAnySpectrumMessage = true
                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM PONG"
                            )
                        }

                        override fun onErrorMessage(message: String) {
                            gotAnySpectrumMessage = true
                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM ERROR: $message"
                            )
                        }

                        override fun onFailure(message: String) {
                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM WS ERROR: $message"
                            )
                        }

                        override fun onClosed() {
                            _uiState.value = _uiState.value.copy(
                                statusText = "SPECTRUM WS CLOSED"
                            )
                        }
                    }
                )
            }
        }

        override fun onFailure(message: String) {
            _uiState.value = _uiState.value.copy(
                isConnected = false,
                statusText = "AUDIO WS ERROR: $message"
            )
        }

        override fun onClosed() {
            _uiState.value = _uiState.value.copy(
                isConnected = false,
                statusText = "AUDIO WS CLOSED"
            )
        }
    }

    private fun connectAudioSession(
        frequencyHz: Long,
        mode: RadioMode,
        bandwidthLowHz: Int,
        bandwidthHighHz: Int
    ) {
        sessionRepository.connectAudio(
            baseUrl = baseUrl,
            frequencyHz = frequencyHz,
            mode = mode.wireValue,
            bandwidthLowHz = bandwidthLowHz,
            bandwidthHighHz = bandwidthHighHz,
            listener = audioListener
        )
    }

    private fun deriveBandwidthForMode(mode: RadioMode): ModeBandwidth {
        return when (mode) {
            RadioMode.USB -> ModeBandwidth(lowHz = 0, highHz = 2700)
            RadioMode.LSB -> ModeBandwidth(lowHz = -2700, highHz = 0)
            RadioMode.CWU -> ModeBandwidth(lowHz = 0, highHz = 600)
            RadioMode.AM -> ModeBandwidth(lowHz = -4000, highHz = 4000)
        }
    }

    private fun loadBands() {
        viewModelScope.launch {
            runCatching { sessionRepository.getBands() }
                .onSuccess { bands ->
                    _uiState.value = _uiState.value.copy(
                        availableBands = bands
                    )
                }
        }
    }

    private fun deriveModeForBand(band: BandDto, centerFreqHz: Long): RadioMode {
        val explicitMode = band.mode
            ?.takeIf { it.isNotBlank() }
            ?.let { RadioMode.fromWireValue(it) }
        if (explicitMode != null) {
            return explicitMode
        }

        // Match static/app.js setBand(): LSB below 10 MHz, USB at 10 MHz and above.
        return if (centerFreqHz < 10_000_000L) RadioMode.LSB else RadioMode.USB
    }

    fun connect() {
        _uiState.value = _uiState.value.copy(
            statusText = "Connecting..."
        )

        viewModelScope.launch {
            val result = sessionRepository.bootstrapSession()

            if (!result.allowed) {
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    statusText = "ERROR: ${result.reason}"
                )
                return@launch
            }

            val resolvedMode = RadioMode.fromWireValue(result.defaultMode ?: "usb")
            val resolvedFrequency = result.defaultFrequency ?: _uiState.value.frequencyHz
            val resolvedBandwidth = deriveBandwidthForMode(resolvedMode)

            _uiState.value = _uiState.value.copy(
                isConnected = true,
                frequencyHz = resolvedFrequency,
                mode = resolvedMode,
                bandwidthLowHz = resolvedBandwidth.lowHz,
                bandwidthHighHz = resolvedBandwidth.highHz,
                statusText = "AUDIO WS CONNECTING"
            )

            connectAudioSession(
                frequencyHz = resolvedFrequency,
                mode = resolvedMode,
                bandwidthLowHz = resolvedBandwidth.lowHz,
                bandwidthHighHz = resolvedBandwidth.highHz
            )
        }
    }

    fun tune(frequencyHz: Long) {
        val bandwidth = deriveBandwidthForMode(_uiState.value.mode)
        _uiState.value = _uiState.value.copy(
            frequencyHz = frequencyHz,
            bandwidthLowHz = bandwidth.lowHz,
            bandwidthHighHz = bandwidth.highHz,
            statusText = "Tune requested"
        )

        if (_uiState.value.isConnected) {
            if (sessionRepository.hasActiveAudioConnection()) {
                sessionRepository.sendAudioTune(
                    frequencyHz = frequencyHz,
                    mode = _uiState.value.mode.wireValue,
                    bandwidthLowHz = bandwidth.lowHz,
                    bandwidthHighHz = bandwidth.highHz
                )
            } else {
                connectAudioSession(
                    frequencyHz = frequencyHz,
                    mode = _uiState.value.mode,
                    bandwidthLowHz = bandwidth.lowHz,
                    bandwidthHighHz = bandwidth.highHz
                )
            }
        }
    }

    fun changeMode(mode: RadioMode) {
        val updatedBandwidth = deriveBandwidthForMode(mode)
        _uiState.value = _uiState.value.copy(
            mode = mode,
            bandwidthLowHz = updatedBandwidth.lowHz,
            bandwidthHighHz = updatedBandwidth.highHz,
            statusText = "Mode change requested"
        )

        if (_uiState.value.isConnected) {
            if (sessionRepository.hasActiveAudioConnection()) {
                sessionRepository.sendAudioTune(
                    frequencyHz = _uiState.value.frequencyHz,
                    mode = mode.wireValue,
                    bandwidthLowHz = updatedBandwidth.lowHz,
                    bandwidthHighHz = updatedBandwidth.highHz
                )
            } else {
                connectAudioSession(
                    frequencyHz = _uiState.value.frequencyHz,
                    mode = mode,
                    bandwidthLowHz = updatedBandwidth.lowHz,
                    bandwidthHighHz = updatedBandwidth.highHz
                )
            }
        }
    }

    fun changeFilter(lowHz: Int, highHz: Int) {
        _uiState.value = _uiState.value.copy(
            bandwidthLowHz = lowHz,
            bandwidthHighHz = highHz,
            statusText = "Filter change requested"
        )
    }

    fun zoomInSpectrum() {
        val currentBinBandwidthHz = _uiState.value.spectrumBinBandwidthHz ?: return
        val targetCenterFreqHz = _uiState.value.frequencyHz
            .takeIf { it > 0L }
            ?: _uiState.value.spectrumCenterFreqHz
            ?: return
        val targetBinBandwidthHz = currentBinBandwidthHz / 2.0

        sessionRepository.sendSpectrumZoom(
            centerFreqHz = targetCenterFreqHz,
            binBandwidthHz = targetBinBandwidthHz
        )

        _uiState.value = _uiState.value.copy(
            statusText = "SPECTRUM ZOOM + cf=$targetCenterFreqHz binBw=$targetBinBandwidthHz"
        )
    }

    fun zoomMaxSpectrum() {
        val targetCenterFreqHz = _uiState.value.frequencyHz
            .takeIf { it > 0L }
            ?: _uiState.value.spectrumCenterFreqHz
            ?: return

        // Ask the backend for the tightest possible span in one step and let it clamp
        // to the maximum valid zoom for the current receiver/spectrum settings.
        val targetBinBandwidthHz = 1.0

        sessionRepository.sendSpectrumZoom(
            centerFreqHz = targetCenterFreqHz,
            binBandwidthHz = targetBinBandwidthHz
        )

        _uiState.value = _uiState.value.copy(
            statusText = "SPECTRUM ZOOM MAX cf=$targetCenterFreqHz binBw=$targetBinBandwidthHz"
        )
    }

    fun zoomOutSpectrum() {
        val currentBinBandwidthHz = _uiState.value.spectrumBinBandwidthHz
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return
        val maxBinBandwidthHz = maxObservedSpectrumBinBandwidthHz
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: currentBinBandwidthHz
        val targetCenterFreqHz = _uiState.value.frequencyHz
            .takeIf { it > 0L }
            ?: _uiState.value.spectrumCenterFreqHz
            ?: return
        if (currentBinBandwidthHz >= maxBinBandwidthHz) {
            _uiState.value = _uiState.value.copy(
                statusText = "SPECTRUM ZOOM - ignored at max range"
            )
            return
        }
        val targetBinBandwidthHz = (currentBinBandwidthHz * 2.0)
            .coerceAtMost(maxBinBandwidthHz)

        sessionRepository.sendSpectrumZoom(
            centerFreqHz = targetCenterFreqHz,
            binBandwidthHz = targetBinBandwidthHz
        )

        _uiState.value = _uiState.value.copy(
            statusText = "SPECTRUM ZOOM - cf=$targetCenterFreqHz binBw=$targetBinBandwidthHz"
        )
    }

    fun centerSpectrumOnTargetFrequency() {
        val targetCenterFreqHz = _uiState.value.frequencyHz
            .takeIf { it > 0L }
            ?: _uiState.value.spectrumCenterFreqHz
            ?: return

        sessionRepository.sendSpectrumPan(
            centerFreqHz = targetCenterFreqHz
        )

        _uiState.value = _uiState.value.copy(
            statusText = "SPECTRUM PAN cf=$targetCenterFreqHz"
        )
    }

    fun selectBand(label: String) {
        val band = _uiState.value.availableBands.firstOrNull { it.label == label } ?: return
        val centerFreqHz = ((band.start + band.end) / 2L)
        val bandWidthHz = (band.end - band.start).toDouble()
        val targetMode = deriveModeForBand(band, centerFreqHz)
        val spectrumBinCount = _uiState.value.spectrumBinCount
        val targetBinBandwidthHz = if (
            spectrumBinCount != null &&
            spectrumBinCount > 0 &&
            bandWidthHz.isFinite() &&
            bandWidthHz > 0.0
        ) {
            bandWidthHz / spectrumBinCount.toDouble()
        } else {
            null
        }

        changeMode(targetMode)
        tune(centerFreqHz)

        if (targetBinBandwidthHz != null && targetBinBandwidthHz.isFinite() && targetBinBandwidthHz > 0.0) {
            sessionRepository.sendSpectrumZoom(
                centerFreqHz = centerFreqHz,
                binBandwidthHz = targetBinBandwidthHz
            )

            _uiState.value = _uiState.value.copy(
                statusText = "SPECTRUM BAND cf=$centerFreqHz binBw=$targetBinBandwidthHz"
            )
        } else {
            centerSpectrumOnTargetFrequency()
        }
    }

    fun setAudioVolume(volume: Float) {
        sessionRepository.setAudioVolume(volume)
        _uiState.value = _uiState.value.copy(
            audioVolume = volume,
            statusText = "Audio volume ${(volume * 100).toInt()}%"
        )
    }

    fun toggleMute() {
        val muted = !_uiState.value.audioMuted
        sessionRepository.setAudioMuted(muted)
        _uiState.value = _uiState.value.copy(
            audioMuted = muted,
            statusText = if (muted) "Audio muted" else "Audio unmuted"
        )
    }

    fun dispatch(action: RadioAction) {
        when (action) {
            RadioAction.Connect -> connect()
            is RadioAction.Tune -> tune(action.frequencyHz)
            is RadioAction.ChangeMode -> changeMode(action.mode)
            is RadioAction.ChangeFilter -> changeFilter(action.lowHz, action.highHz)
        }
    }

    override fun onCleared() {
        sessionRepository.disconnect()
        super.onCleared()
    }
}
