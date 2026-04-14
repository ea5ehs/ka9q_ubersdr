package es.niceto.ubersdr.presentation.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    private val baseUrl = "https://ubersdr.niceto.es/"

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

            _uiState.value = _uiState.value.copy(
                isConnected = true,
                frequencyHz = resolvedFrequency,
                mode = resolvedMode,
                statusText = "AUDIO WS CONNECTING"
            )

            sessionRepository.connectAudio(
                baseUrl = baseUrl,
                frequencyHz = resolvedFrequency,
                mode = resolvedMode.wireValue,
                bandwidthLowHz = _uiState.value.bandwidthLowHz,
                bandwidthHighHz = _uiState.value.bandwidthHighHz,
                listener = object : AudioWsClient.Listener {
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
            )
        }
    }

    fun tune(frequencyHz: Long) {
        _uiState.value = _uiState.value.copy(
            frequencyHz = frequencyHz,
            statusText = "Tune requested"
        )
    }

    fun changeMode(mode: RadioMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            statusText = "Mode change requested"
        )
    }

    fun changeFilter(lowHz: Int, highHz: Int) {
        _uiState.value = _uiState.value.copy(
            bandwidthLowHz = lowHz,
            bandwidthHighHz = highHz,
            statusText = "Filter change requested"
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
