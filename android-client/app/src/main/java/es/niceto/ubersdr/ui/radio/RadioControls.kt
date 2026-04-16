package es.niceto.ubersdr.ui.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Composable
fun RadioControls(
    audioVolume: Float,
    audioMuted: Boolean,
    onAudioVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color(0xFF121922))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(35.dp)
            ) {
                Icon(
                    imageVector = SpeakerIcon,
                    contentDescription = if (audioMuted) "Muted" else "Volume",
                    modifier = Modifier.size(25.dp),
                    tint = if (audioMuted) {
                        Color(0xFFE35B5B)
                    } else {
                        Color(0xFF5EDC6A)
                    }
                )
            }

            Slider(
                value = audioVolume,
                onValueChange = onAudioVolumeChanged,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(25.dp)
                    .graphicsLayer(scaleY = 0.6f)
            )

            Text(
                text = "${(audioVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB8C4CF),
                modifier = Modifier.width(50.dp)
            )
        }
    }
}

private val SpeakerIcon: ImageVector =
    ImageVector.Builder(
        name = "SpeakerIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(3f, 10f)
            lineTo(7f, 10f)
            lineTo(12f, 6f)
            lineTo(12f, 18f)
            lineTo(7f, 14f)
            lineTo(3f, 14f)
            close()
            moveTo(15f, 9f)
            lineTo(15f, 15f)
            lineTo(16.5f, 15f)
            curveTo(18.4f, 15f, 20f, 13.4f, 20f, 11.5f)
            curveTo(20f, 9.6f, 18.4f, 8f, 16.5f, 8f)
            lineTo(15f, 8f)
            close()
        }
    }.build()
