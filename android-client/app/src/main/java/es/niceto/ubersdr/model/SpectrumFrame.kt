package es.niceto.ubersdr.model

data class SpectrumFrame(
    val timestampNs: Long = 0L,
    val frequencyHz: Long = 0L,
    val isDelta: Boolean = false,
    val values: List<Int> = emptyList()
)
