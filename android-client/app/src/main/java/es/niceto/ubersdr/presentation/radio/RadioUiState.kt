package es.niceto.ubersdr.presentation.radio

import es.niceto.ubersdr.model.RadioMode

data class RadioUiState(
    val frequencyHz: Long = 14_175_000L,
    val mode: RadioMode = RadioMode.USB,
    val bandwidthLowHz: Int = 50,
    val bandwidthHighHz: Int = 2700,
    val isConnected: Boolean = false,
    val statusText: String = "Idle"
)
