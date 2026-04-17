package es.niceto.ubersdr.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.niceto.ubersdr.model.RadioMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val frequencyHz: Long = DEFAULT_FREQUENCY_HZ,
    val mode: RadioMode = DEFAULT_MODE,
    val tuningStepHz: Long = DEFAULT_TUNING_STEP_HZ,
    val spectrumZoomBinBandwidthHz: Double? = null,
    val audioVolume: Float = DEFAULT_AUDIO_VOLUME,
    val audioMuted: Boolean = DEFAULT_AUDIO_MUTED,
    val keepScreenOn: Boolean = DEFAULT_KEEP_SCREEN_ON,
    val cwAutoTuneAveraging: Int = DEFAULT_CW_AUTOTUNE_AVERAGING
)

const val DEFAULT_FREQUENCY_HZ = 14_175_000L
val DEFAULT_MODE = RadioMode.USB
const val DEFAULT_TUNING_STEP_HZ = 1_000L
const val DEFAULT_AUDIO_VOLUME = 1f
const val DEFAULT_AUDIO_MUTED = false
const val DEFAULT_KEEP_SCREEN_ON = false
const val DEFAULT_CW_AUTOTUNE_AVERAGING = 6

class AppSettingsStore(private val context: Context) {
    private companion object {
        val FREQUENCY_KEY = longPreferencesKey("frequency_hz")
        val MODE_KEY = stringPreferencesKey("mode")
        val TUNING_STEP_KEY = longPreferencesKey("tuning_step_hz")
        val SPECTRUM_ZOOM_BIN_BANDWIDTH_KEY = stringPreferencesKey("spectrum_zoom_bin_bandwidth_hz")
        val AUDIO_VOLUME_KEY = floatPreferencesKey("audio_volume")
        val AUDIO_MUTED_KEY = booleanPreferencesKey("audio_muted")
        val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        val CW_AUTOTUNE_AVERAGING_KEY = intPreferencesKey("cw_autotune_averaging")
        val VALID_TUNING_STEPS = setOf(10L, 100L, 500L, 1_000L, 5_000L, 10_000L)
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch {
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { preferences ->
            AppSettings(
                frequencyHz = preferences[FREQUENCY_KEY]
                    ?.takeIf { it in 10_000L..30_000_000L }
                    ?: DEFAULT_FREQUENCY_HZ,
                mode = preferences[MODE_KEY]
                    ?.let { RadioMode.fromWireValue(it) }
                    ?: DEFAULT_MODE,
                tuningStepHz = preferences[TUNING_STEP_KEY]
                    ?.takeIf { it in VALID_TUNING_STEPS }
                    ?: DEFAULT_TUNING_STEP_HZ,
                spectrumZoomBinBandwidthHz = preferences[SPECTRUM_ZOOM_BIN_BANDWIDTH_KEY]
                    ?.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it >= 1.0 },
                audioVolume = preferences[AUDIO_VOLUME_KEY]
                    ?.takeIf { it in 0f..1f }
                    ?: DEFAULT_AUDIO_VOLUME,
                audioMuted = preferences[AUDIO_MUTED_KEY] ?: DEFAULT_AUDIO_MUTED,
                keepScreenOn = preferences[KEEP_SCREEN_ON_KEY] ?: DEFAULT_KEEP_SCREEN_ON,
                cwAutoTuneAveraging = preferences[CW_AUTOTUNE_AVERAGING_KEY]
                    ?.takeIf { it in 1..10 }
                    ?: DEFAULT_CW_AUTOTUNE_AVERAGING
            )
        }

    suspend fun saveFrequency(frequencyHz: Long) {
        context.dataStore.edit { it[FREQUENCY_KEY] = frequencyHz }
    }

    suspend fun saveMode(mode: RadioMode) {
        context.dataStore.edit { it[MODE_KEY] = mode.wireValue }
    }

    suspend fun saveTuningStep(stepHz: Long) {
        context.dataStore.edit { it[TUNING_STEP_KEY] = stepHz }
    }

    suspend fun saveSpectrumZoomBinBandwidthHz(binBandwidthHz: Double) {
        context.dataStore.edit {
            it[SPECTRUM_ZOOM_BIN_BANDWIDTH_KEY] = binBandwidthHz.toString()
        }
    }

    suspend fun saveAudioVolume(volume: Float) {
        context.dataStore.edit { it[AUDIO_VOLUME_KEY] = volume }
    }

    suspend fun saveAudioMuted(muted: Boolean) {
        context.dataStore.edit { it[AUDIO_MUTED_KEY] = muted }
    }

    suspend fun saveKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEEP_SCREEN_ON_KEY] = enabled }
    }

    suspend fun saveCwAutoTuneAveraging(averaging: Int) {
        context.dataStore.edit {
            it[CW_AUTOTUNE_AVERAGING_KEY] = averaging.coerceIn(1, 10)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
