package es.niceto.ubersdr.presentation.radio

import androidx.lifecycle.ViewModel
import es.niceto.ubersdr.model.RadioMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RadioViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    fun connect() {
        _uiState.value = _uiState.value.copy(
            isConnected = true,
            statusText = "Connect requested"
        )
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
}
