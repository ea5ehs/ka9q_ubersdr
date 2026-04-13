package es.niceto.ubersdr.ui.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.niceto.ubersdr.model.RadioMode
import es.niceto.ubersdr.presentation.radio.RadioViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val frequencyText = NumberFormat.getNumberInstance(Locale.US).format(uiState.frequencyHz)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = frequencyText,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Mode: ${uiState.mode.wireValue.uppercase()}",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Status: ${uiState.statusText}",
            style = MaterialTheme.typography.bodyMedium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Color(0xFF102030)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Waterfall placeholder",
                color = Color.White
            )
        }

        Text(
            text = "Filter: ${uiState.bandwidthLowHz} / ${uiState.bandwidthHighHz} Hz",
            style = MaterialTheme.typography.bodyMedium
        )

        RadioControls(
            onConnect = { viewModel.connect() },
            onTuneUp = { viewModel.tune(uiState.frequencyHz + 1_000L) },
            onTuneDown = { viewModel.tune(uiState.frequencyHz - 1_000L) },
            onModeSelected = { mode: RadioMode -> viewModel.changeMode(mode) }
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}
