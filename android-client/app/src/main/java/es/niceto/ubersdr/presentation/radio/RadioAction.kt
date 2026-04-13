package es.niceto.ubersdr.presentation.radio

import es.niceto.ubersdr.model.RadioMode

sealed interface RadioAction {
    data object Connect : RadioAction
    data class Tune(val frequencyHz: Long) : RadioAction
    data class ChangeMode(val mode: RadioMode) : RadioAction
    data class ChangeFilter(val lowHz: Int, val highHz: Int) : RadioAction
}
