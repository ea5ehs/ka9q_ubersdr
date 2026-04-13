package es.niceto.ubersdr.model

data class RadioState(
    val frequencyHz: Long = 14_175_000L,
    val mode: RadioMode = RadioMode.USB,
    val bandwidthLowHz: Int = 50,
    val bandwidthHighHz: Int = 2700,
    val visibleCenterFrequencyHz: Long = 14_175_000L,
    val isConnected: Boolean = false
)
