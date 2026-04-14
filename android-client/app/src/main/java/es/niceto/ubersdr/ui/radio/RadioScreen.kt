package es.niceto.ubersdr.ui.radio

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    var waterfallBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uiState.latestSpectrumRow, uiState.spectrumBinCount) {
        val row = uiState.latestSpectrumRow
        val width = uiState.spectrumBinCount

        if (row != null && width != null && width > 0 && row.size == width) {
            val height = 256

            val bitmap = waterfallBitmap
            val targetBitmap = if (bitmap == null || bitmap.width != width || bitmap.height != height) {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            }

            val oldPixels = IntArray(width * height)
            targetBitmap.getPixels(oldPixels, 0, width, 0, 0, width, height)

            for (y in height - 1 downTo 1) {
                val srcOffset = (y - 1) * width
                val dstOffset = y * width
                System.arraycopy(oldPixels, srcOffset, oldPixels, dstOffset, width)
            }

            for (x in 0 until width) {
                val v = row[x].toInt() and 0xFF
                oldPixels[x] = android.graphics.Color.argb(255, v, v, v)
            }

            targetBitmap.setPixels(oldPixels, 0, width, 0, 0, width, height)
            waterfallBitmap = targetBitmap
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
        Text(
            text = "Spectrum center: ${uiState.spectrumCenterFreqHz ?: "-"} Hz",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Spectrum bins: ${uiState.spectrumBinCount ?: "-"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Spectrum bin BW: ${uiState.spectrumBinBandwidthHz ?: "-"} Hz",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Spectrum total BW: ${uiState.spectrumTotalBandwidthHz ?: "-"} Hz",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "SPEC frames received: ${uiState.specFramesReceived}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Last SPEC total: ${uiState.lastSpecFrameSize ?: "-"} bytes",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Last SPEC payload: ${uiState.lastSpecPayloadSize ?: "-"} bytes",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "SPEC last flags: ${uiState.specLastFlags ?: "-"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "SPEC buffer: ${uiState.specBufferSize ?: "-"} match=${uiState.specBufferMatchesBinCount}",
            style = MaterialTheme.typography.bodyMedium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(Color(0xFF102030)),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = waterfallBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Waterfall",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Text(
                    text = "Waterfall placeholder",
                    color = Color.White
                )
            }
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
