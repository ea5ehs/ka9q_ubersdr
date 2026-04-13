package es.niceto.ubersdr.ui.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.niceto.ubersdr.model.RadioMode

@Composable
fun RadioControls(
    onConnect: () -> Unit,
    onTuneUp: () -> Unit,
    onTuneDown: () -> Unit,
    onModeSelected: (RadioMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = onConnect) {
            Text("Connect")
        }
        Button(onClick = onTuneDown) {
            Text("-1 kHz")
        }
        Button(onClick = onTuneUp) {
            Text("+1 kHz")
        }
        Button(onClick = { onModeSelected(RadioMode.USB) }) {
            Text("USB")
        }
    }
}
