package es.niceto.ubersdr.presentation.radio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.niceto.ubersdr.app.AppSettingsStore
import es.niceto.ubersdr.app.DEFAULT_AUDIO_MUTED
import es.niceto.ubersdr.app.DEFAULT_AUDIO_VOLUME
import es.niceto.ubersdr.app.DEFAULT_CW_AUTOTUNE_AVERAGING
import es.niceto.ubersdr.app.DEFAULT_FREQUENCY_HZ
import es.niceto.ubersdr.app.DEFAULT_KEEP_SCREEN_ON
import es.niceto.ubersdr.app.DEFAULT_MODE
import es.niceto.ubersdr.app.DEFAULT_TUNING_STEP_HZ
import es.niceto.ubersdr.data.network.dto.BandDto
import es.niceto.ubersdr.data.websocket.AudioWsClient
import es.niceto.ubersdr.data.websocket.SpectrumWsClient
import es.niceto.ubersdr.model.RadioMode
import es.niceto.ubersdr.session.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RadioViewModel(
    private val sessionRepository: SessionRepository,
    private val settingsStore: AppSettingsStore
) : ViewModel() {
    private companion object {
        const val TAG = "UberSDR-RadioVM"
        const val MIN_VALID_SPECTRUM_FREQ_HZ = 10_000L
        const val MAX_VALID_SPECTRUM_FREQ_HZ = 30_000_000L
    }

    private data class ModeBandwidth(
        val lowHz: Int,
        val highHz: Int
    )

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()
    private var maxObservedSpectrumBinBandwidthHz: Double? = null
    private var maxObservedSpectrumTotalBandwidthHz: Double? = null
    private var pendingPersistedSpectrumCenterFreqHz: Long? = null
    private var pendingPersistedSpectrumZoomBinBandwidthHz: Double? = null

    private val baseUrl = "https://ubersdr.niceto.es/"

    init {
        loadBands()
        restorePersistedSettings()
    }

    private val audioListener = object : AudioWsClient.Listener {
        override fun onConnecting() {
            Log.d(TAG, "audioListener.onConnecting")
            _uiState.value = _uiState.value.copy(
                statusText = "AUDIO WS CONNECTING"
            )
        }

        override fun onOpen() {
            Log.d(TAG, "audioListener.onOpen")
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
                            val validTotalBandwidthHz = config.totalBandwidthHz
                                ?.takeIf { it.isFinite() && it > 0.0 }
                            if (validTotalBandwidthHz != null) {
                                maxObservedSpectrumTotalBandwidthHz = maxOf(
                                    maxObservedSpectrumTotalBandwidthHz ?: validTotalBandwidthHz,
                                    validTotalBandwidthHz
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

                            val restoredZoomBinBandwidthHz = pendingPersistedSpectrumZoomBinBandwidthHz
                            val restoreCenterFreqHz = pendingPersistedSpectrumCenterFreqHz
                                ?.takeIf { it in MIN_VALID_SPECTRUM_FREQ_HZ..MAX_VALID_SPECTRUM_FREQ_HZ }
                                ?: _uiState.value.frequencyHz.takeIf {
                                    it in MIN_VALID_SPECTRUM_FREQ_HZ..MAX_VALID_SPECTRUM_FREQ_HZ
                                }
                            val maxZoomOutBinBandwidthHz = maxObservedSpectrumBinBandwidthHz
                            if (
                                restoredZoomBinBandwidthHz != null &&
                                restoreCenterFreqHz != null &&
                                maxZoomOutBinBandwidthHz != null &&
                                restoredZoomBinBandwidthHz.isFinite() &&
                                restoredZoomBinBandwidthHz >= 1.0 &&
                                maxZoomOutBinBandwidthHz.isFinite() &&
                                maxZoomOutBinBandwidthHz > 0.0
                            ) {
                                pendingPersistedSpectrumCenterFreqHz = null
                                pendingPersistedSpectrumZoomBinBandwidthHz = null
                                sessionRepository.sendSpectrumZoom(
                                    centerFreqHz = restoreCenterFreqHz,
                                    binBandwidthHz = restoredZoomBinBandwidthHz.coerceIn(1.0, maxZoomOutBinBandwidthHz)
                                )
                            }
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
            Log.w(TAG, "audioListener.onFailure: $message")
            _uiState.value = _uiState.value.copy(
                isConnected = false,
                statusText = "AUDIO WS ERROR: $message"
            )
        }

        override fun onClosed() {
            Log.d(TAG, "audioListener.onClosed")
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
            RadioMode.USB -> ModeBandwidth(lowHz = 150, highHz = 2700)
            RadioMode.LSB -> ModeBandwidth(lowHz = -2700, highHz = -150)
            RadioMode.CWU -> ModeBandwidth(lowHz = -250, highHz = 250)
            RadioMode.CWL -> ModeBandwidth(lowHz = -250, highHz = 250)
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

    private fun restorePersistedSettings() {
        viewModelScope.launch {
            runCatching { settingsStore.settings.first() }
                .onSuccess { settings ->
                    val restoredBandwidth = deriveBandwidthForMode(settings.mode)
                    sessionRepository.setAudioVolume(settings.audioVolume)
                    sessionRepository.setAudioMuted(settings.audioMuted)
                    _uiState.value = _uiState.value.copy(
                        frequencyHz = settings.frequencyHz,
                        mode = settings.mode,
                        bandwidthLowHz = restoredBandwidth.lowHz,
                        bandwidthHighHz = restoredBandwidth.highHz,
                        audioVolume = settings.audioVolume,
                        audioMuted = settings.audioMuted,
                        tuningStepHz = settings.tuningStepHz,
                        keepScreenOn = settings.keepScreenOn,
                        cwAutoTuneAveraging = settings.cwAutoTuneAveraging
                    )
                    pendingPersistedSpectrumCenterFreqHz = settings.frequencyHz
                        .takeIf { it in MIN_VALID_SPECTRUM_FREQ_HZ..MAX_VALID_SPECTRUM_FREQ_HZ }
                    pendingPersistedSpectrumZoomBinBandwidthHz = settings.spectrumZoomBinBandwidthHz
                }
                .onFailure {
                    Log.w(TAG, "restorePersistedSettings() failed: ${it.message}")
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
        Log.d(
            TAG,
            "connect() requested isConnected=${_uiState.value.isConnected} " +
                "muted=${_uiState.value.audioMuted} volume=${_uiState.value.audioVolume}"
        )
        _uiState.value = _uiState.value.copy(
            statusText = "Connecting..."
        )

        viewModelScope.launch {
            val result = sessionRepository.bootstrapSession()

            if (!result.allowed) {
                Log.w(TAG, "connect() rejected reason=${result.reason}")
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    statusText = "ERROR: ${result.reason}"
                )
                return@launch
            }

            val resolvedMode = _uiState.value.mode
            val resolvedFrequency = _uiState.value.frequencyHz
                .takeIf { it in MIN_VALID_SPECTRUM_FREQ_HZ..MAX_VALID_SPECTRUM_FREQ_HZ }
                ?: result.defaultFrequency
                ?: DEFAULT_FREQUENCY_HZ
            val resolvedBandwidth = deriveBandwidthForMode(resolvedMode)

            _uiState.value = _uiState.value.copy(
                isConnected = true,
                frequencyHz = resolvedFrequency,
                mode = resolvedMode,
                bandwidthLowHz = resolvedBandwidth.lowHz,
                bandwidthHighHz = resolvedBandwidth.highHz,
                statusText = "AUDIO WS CONNECTING"
            )

            Log.d(
                TAG,
                "connect() bootstrap ok session=${result.sessionId} " +
                    "freq=$resolvedFrequency mode=${resolvedMode.wireValue}"
            )
            connectAudioSession(
                frequencyHz = resolvedFrequency,
                mode = resolvedMode,
                bandwidthLowHz = resolvedBandwidth.lowHz,
                bandwidthHighHz = resolvedBandwidth.highHz
            )
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect() requested")
        sessionRepository.disconnect()
        _uiState.value = _uiState.value.copy(
            isConnected = false,
            statusText = "Disconnected"
        )
    }

    fun togglePower() {
        Log.d(TAG, "togglePower() currentConnected=${_uiState.value.isConnected}")
        if (_uiState.value.isConnected) {
            disconnect()
        } else {
            connect()
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

        viewModelScope.launch {
            settingsStore.saveFrequency(frequencyHz)
        }
    }

    fun changeMode(mode: RadioMode) {
        val updatedBandwidth = deriveBandwidthForMode(mode)
        val updatedTuningStepHz = if (mode.wireValue.startsWith("cw")) 10L else 1_000L
        _uiState.value = _uiState.value.copy(
            mode = mode,
            bandwidthLowHz = updatedBandwidth.lowHz,
            bandwidthHighHz = updatedBandwidth.highHz,
            tuningStepHz = updatedTuningStepHz,
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

        viewModelScope.launch {
            settingsStore.saveMode(mode)
            settingsStore.saveTuningStep(updatedTuningStepHz)
        }
    }

    fun changeFilter(lowHz: Int, highHz: Int) {
        _uiState.value = _uiState.value.copy(
            bandwidthLowHz = lowHz,
            bandwidthHighHz = highHz,
            statusText = "Filter change requested"
        )

        if (_uiState.value.isConnected) {
            if (sessionRepository.hasActiveAudioConnection()) {
                sessionRepository.sendAudioTune(
                    frequencyHz = _uiState.value.frequencyHz,
                    mode = _uiState.value.mode.wireValue,
                    bandwidthLowHz = lowHz,
                    bandwidthHighHz = highHz
                )
            } else {
                connectAudioSession(
                    frequencyHz = _uiState.value.frequencyHz,
                    mode = _uiState.value.mode,
                    bandwidthLowHz = lowHz,
                    bandwidthHighHz = highHz
                )
            }
        }
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

        persistSpectrumZoom(targetBinBandwidthHz)
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

        persistSpectrumZoom(targetBinBandwidthHz)
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

        persistSpectrumZoom(targetBinBandwidthHz)
    }

    fun zoomMinSpectrum() {
        val currentBinBandwidthHz = _uiState.value.spectrumBinBandwidthHz
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return
        val maxBinBandwidthHz = maxObservedSpectrumBinBandwidthHz
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: currentBinBandwidthHz
        val maxTotalBandwidthHz = maxObservedSpectrumTotalBandwidthHz
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: _uiState.value.spectrumTotalBandwidthHz
            ?: return
        val requestedCenterFreqHz = _uiState.value.frequencyHz
            .takeIf { it > 0L }
            ?: _uiState.value.spectrumCenterFreqHz
            ?: return
        val halfSpanHz = maxTotalBandwidthHz / 2.0
        val minCenterHz = MIN_VALID_SPECTRUM_FREQ_HZ.toDouble() + halfSpanHz
        val maxCenterHz = MAX_VALID_SPECTRUM_FREQ_HZ.toDouble() - halfSpanHz
        val targetCenterFreqHz = if (minCenterHz <= maxCenterHz) {
            requestedCenterFreqHz.toDouble()
                .coerceIn(minCenterHz, maxCenterHz)
                .toLong()
        } else {
            ((MIN_VALID_SPECTRUM_FREQ_HZ + MAX_VALID_SPECTRUM_FREQ_HZ) / 2L)
        }
        val currentCenterFreqHz = _uiState.value.spectrumCenterFreqHz
        if (currentBinBandwidthHz >= maxBinBandwidthHz && currentCenterFreqHz == targetCenterFreqHz) {
            _uiState.value = _uiState.value.copy(
                statusText = "SPECTRUM ZOOM MIN ignored at max range"
            )
            return
        }

        sessionRepository.sendSpectrumZoom(
            centerFreqHz = targetCenterFreqHz,
            binBandwidthHz = maxBinBandwidthHz
        )

        _uiState.value = _uiState.value.copy(
            statusText = "SPECTRUM ZOOM MIN cf=$targetCenterFreqHz binBw=$maxBinBandwidthHz"
        )

        persistSpectrumZoom(maxBinBandwidthHz)
    }

    fun panSpectrumTo(centerFreqHz: Long) {
        val totalBandwidthHz = _uiState.value.spectrumTotalBandwidthHz
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return
        val halfSpanHz = totalBandwidthHz / 2.0
        val minCenterHz = MIN_VALID_SPECTRUM_FREQ_HZ.toDouble() + halfSpanHz
        val maxCenterHz = MAX_VALID_SPECTRUM_FREQ_HZ.toDouble() - halfSpanHz
        val targetCenterFreqHz = if (minCenterHz <= maxCenterHz) {
            centerFreqHz.toDouble()
                .coerceIn(minCenterHz, maxCenterHz)
                .toLong()
        } else {
            ((MIN_VALID_SPECTRUM_FREQ_HZ + MAX_VALID_SPECTRUM_FREQ_HZ) / 2L)
        }

        sessionRepository.sendSpectrumPan(
            centerFreqHz = targetCenterFreqHz
        )

        _uiState.value = _uiState.value.copy(
            spectrumCenterFreqHz = targetCenterFreqHz,
            statusText = "SPECTRUM PAN cf=$targetCenterFreqHz"
        )
    }

    fun dragTuneSpectrumTo(frequencyHz: Long): Long {
        val targetFrequencyHz = frequencyHz.coerceIn(
            MIN_VALID_SPECTRUM_FREQ_HZ,
            MAX_VALID_SPECTRUM_FREQ_HZ
        )
        val totalBandwidthHz = _uiState.value.spectrumTotalBandwidthHz
            ?.takeIf { it.isFinite() && it > 0.0 }
        val targetCenterFreqHz = if (totalBandwidthHz != null) {
            val halfSpanHz = totalBandwidthHz / 2.0
            val minCenterHz = MIN_VALID_SPECTRUM_FREQ_HZ.toDouble() + halfSpanHz
            val maxCenterHz = MAX_VALID_SPECTRUM_FREQ_HZ.toDouble() - halfSpanHz
            if (minCenterHz <= maxCenterHz) {
                targetFrequencyHz.toDouble()
                    .coerceIn(minCenterHz, maxCenterHz)
                    .toLong()
            } else {
                ((MIN_VALID_SPECTRUM_FREQ_HZ + MAX_VALID_SPECTRUM_FREQ_HZ) / 2L)
            }
        } else {
            targetFrequencyHz
        }
        val bandwidth = deriveBandwidthForMode(_uiState.value.mode)

        Log.d(
            TAG,
            "dragTuneSpectrumTo prev=${_uiState.value.frequencyHz} " +
                "next=$targetFrequencyHz center=$targetCenterFreqHz"
        )

        _uiState.value = _uiState.value.copy(
            frequencyHz = targetFrequencyHz,
            spectrumCenterFreqHz = targetCenterFreqHz,
            bandwidthLowHz = bandwidth.lowHz,
            bandwidthHighHz = bandwidth.highHz,
            statusText = "DRAG TUNE f=$targetFrequencyHz cf=$targetCenterFreqHz"
        )

        if (_uiState.value.isConnected) {
            if (sessionRepository.hasActiveAudioConnection()) {
                sessionRepository.sendAudioTune(
                    frequencyHz = targetFrequencyHz,
                    mode = _uiState.value.mode.wireValue,
                    bandwidthLowHz = bandwidth.lowHz,
                    bandwidthHighHz = bandwidth.highHz
                )
            } else {
                connectAudioSession(
                    frequencyHz = targetFrequencyHz,
                    mode = _uiState.value.mode,
                    bandwidthLowHz = bandwidth.lowHz,
                    bandwidthHighHz = bandwidth.highHz
                )
            }
        }

        sessionRepository.sendSpectrumPan(
            centerFreqHz = targetCenterFreqHz
        )

        return targetFrequencyHz
    }

    fun centerSpectrumOnTargetFrequency() {
        val targetCenterFreqHz = _uiState.value.frequencyHz
            .takeIf { it > 0L }
            ?: _uiState.value.spectrumCenterFreqHz
            ?: return

        panSpectrumTo(targetCenterFreqHz)
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

            persistSpectrumZoom(targetBinBandwidthHz)
        } else {
            centerSpectrumOnTargetFrequency()
        }
    }

    private fun persistSpectrumZoom(binBandwidthHz: Double) {
        val safeBinBandwidthHz = binBandwidthHz
            .takeIf { it.isFinite() && it >= 1.0 }
            ?: return

        viewModelScope.launch {
            settingsStore.saveSpectrumZoomBinBandwidthHz(safeBinBandwidthHz)
        }
    }

    fun setAudioVolume(volume: Float) {
        Log.d(TAG, "setAudioVolume() volume=$volume")
        sessionRepository.setAudioVolume(volume)
        _uiState.value = _uiState.value.copy(
            audioVolume = volume,
            statusText = "Audio volume ${(volume * 100).toInt()}%"
        )

        viewModelScope.launch {
            settingsStore.saveAudioVolume(volume)
        }
    }

    fun toggleMute() {
        val muted = !_uiState.value.audioMuted
        Log.d(TAG, "toggleMute() muted=$muted")
        sessionRepository.setAudioMuted(muted)
        _uiState.value = _uiState.value.copy(
            audioMuted = muted,
            statusText = if (muted) "Audio muted" else "Audio unmuted"
        )

        viewModelScope.launch {
            settingsStore.saveAudioMuted(muted)
        }
    }

    fun setTuningStep(stepHz: Long) {
        val safeStepHz = stepHz.takeIf { it in listOf(10L, 100L, 500L, 1_000L, 5_000L, 10_000L) } ?: DEFAULT_TUNING_STEP_HZ
        _uiState.value = _uiState.value.copy(
            tuningStepHz = safeStepHz
        )

        viewModelScope.launch {
            settingsStore.saveTuningStep(safeStepHz)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            keepScreenOn = enabled
        )

        viewModelScope.launch {
            settingsStore.saveKeepScreenOn(enabled)
        }
    }

    fun setCwAutoTuneAveraging(averaging: Int) {
        val safeAveraging = averaging.coerceIn(1, 10)
        _uiState.value = _uiState.value.copy(
            cwAutoTuneAveraging = safeAveraging
        )

        viewModelScope.launch {
            settingsStore.saveCwAutoTuneAveraging(safeAveraging)
        }
    }

    fun resetPersistedSettings() {
        val defaultBandwidth = deriveBandwidthForMode(DEFAULT_MODE)
        sessionRepository.setAudioVolume(DEFAULT_AUDIO_VOLUME)
        sessionRepository.setAudioMuted(DEFAULT_AUDIO_MUTED)
        _uiState.value = _uiState.value.copy(
            frequencyHz = DEFAULT_FREQUENCY_HZ,
            mode = DEFAULT_MODE,
            bandwidthLowHz = defaultBandwidth.lowHz,
            bandwidthHighHz = defaultBandwidth.highHz,
            audioVolume = DEFAULT_AUDIO_VOLUME,
            audioMuted = DEFAULT_AUDIO_MUTED,
            tuningStepHz = DEFAULT_TUNING_STEP_HZ,
            keepScreenOn = DEFAULT_KEEP_SCREEN_ON,
            cwAutoTuneAveraging = DEFAULT_CW_AUTOTUNE_AVERAGING,
            statusText = "Ajustes restablecidos"
        )

        viewModelScope.launch {
            settingsStore.clear()
        }
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
