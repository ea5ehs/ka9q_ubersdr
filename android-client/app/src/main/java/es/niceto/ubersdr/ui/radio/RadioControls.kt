package es.niceto.ubersdr.ui.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.niceto.ubersdr.model.RadioMode

@Composable
fun RadioControls(
    onConnect: () -> Unit,
    currentMode: RadioMode,
    onModeSelected: (RadioMode) -> Unit,
    audioVolume: Float,
    audioMuted: Boolean,
    onAudioVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit,
    showConnectButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showConnectButton) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onConnect) {
                    Text("Connect")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                RadioMode.USB to "USB",
                RadioMode.LSB to "LSB",
                RadioMode.AM to "AM",
                RadioMode.CWU to "CWU"
            ).forEach { (mode, label) ->
                val active = currentMode == mode
                Button(
                    onClick = { onModeSelected(mode) },
                    colors = if (active) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                ) {
                    Text(label)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onToggleMute) {
                Text(if (audioMuted) "Unmute" else "Mute")
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Volume ${(audioVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = audioVolume,
                    onValueChange = onAudioVolumeChanged,
                    valueRange = 0f..1f
                )
            }
        }
    }
}
