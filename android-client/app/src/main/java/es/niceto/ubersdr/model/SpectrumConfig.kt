package es.niceto.ubersdr.model

data class SpectrumConfig(
    val centerFreq: Long = 0L,
    val binCount: Int = 0,
    val binBandwidth: Float = 0f,
    val totalBandwidth: Double = 0.0,
    val sessionId: String = ""
)
