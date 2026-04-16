package es.niceto.ubersdr.model

enum class RadioMode(val wireValue: String) {
    USB("usb"),
    LSB("lsb"),
    CWU("cwu"),
    CWL("cwl"),
    AM("am");

    companion object {
        fun fromWireValue(value: String): RadioMode =
            entries.firstOrNull { it.wireValue == value } ?: USB
    }
}
