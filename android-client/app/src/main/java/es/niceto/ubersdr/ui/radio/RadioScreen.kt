package es.niceto.ubersdr.ui.radio

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.niceto.ubersdr.model.RadioMode
import es.niceto.ubersdr.presentation.radio.RadioViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    val tuningStepsHz = listOf(10L, 100L, 1_000L, 5_000L, 10_000L)
    val waterfallPalette = WaterfallPalette.Jet
    val bandButtonOrder = listOf("160", "80", "60", "40", "30", "20", "17", "15", "12", "10")
    var tuningStepHz by remember { mutableStateOf(1_000L) }
    var waterfallVisible by remember { mutableStateOf(true) }
    var telemetryExpanded by remember { mutableStateOf(false) }
    var tuningStepMenuExpanded by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    var waterfallBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tappedFrequencyHz by remember { mutableStateOf<Long?>(null) }
    var hoverFrequencyHz by remember { mutableStateOf<Long?>(null) }
    val centerFreq = uiState.spectrumCenterFreqHz
    val totalBandwidthHz = uiState.spectrumTotalBandwidthHz
    val spectrumStartFreq = if (centerFreq != null && totalBandwidthHz != null && totalBandwidthHz > 0.0) {
        centerFreq.toDouble() - totalBandwidthHz / 2.0
    } else {
        null
    }
    val spectrumEndFreq = if (centerFreq != null && totalBandwidthHz != null && totalBandwidthHz > 0.0) {
        centerFreq.toDouble() + totalBandwidthHz / 2.0
    } else {
        null
    }
    val audioMinusSpectrumCenterHz = centerFreq?.let { uiState.frequencyHz - it }
    val tunedFrequencyRatio = if (spectrumStartFreq != null && spectrumEndFreq != null) {
        val ratio = (uiState.frequencyHz.toDouble() - spectrumStartFreq) / (spectrumEndFreq - spectrumStartFreq)
        ratio.takeIf { it in 0.0..1.0 }
    } else {
        null
    }
    val hoverFrequencyRatio = if (hoverFrequencyHz != null && spectrumStartFreq != null && spectrumEndFreq != null) {
        val ratio = (hoverFrequencyHz!!.toDouble() - spectrumStartFreq) / (spectrumEndFreq - spectrumStartFreq)
        ratio.takeIf { it in 0.0..1.0 }
    } else {
        null
    }
    val selectedFrequencyText = NumberFormat.getNumberInstance(Locale.US).format(uiState.frequencyHz)
    val compactHeader = buildString {
        append("${uiState.frequencyHz} Hz")
        append(" | ")
        append(uiState.mode.wireValue.uppercase())
        append(" | ")
        append("C:${centerFreq ?: "-"}")
        append(" | ")
        append("[${spectrumStartFreq?.toLong() ?: "-"} - ${spectrumEndFreq?.toLong() ?: "-"}]")
    }
    val shortStatus = uiState.statusText.replace("\n", " ").take(72)
    val tuningStepLabel = when (tuningStepHz) {
        1_000L -> "1 kHz"
        5_000L -> "5 kHz"
        10_000L -> "10 kHz"
        else -> "$tuningStepHz Hz"
    }
    val bandButtons = bandButtonOrder.mapNotNull { shortLabel ->
        uiState.availableBands.firstOrNull { it.label == "${shortLabel}m" }?.let { band ->
            shortLabel to band
        }
    }

    LaunchedEffect(uiState.latestSpectrumRow, uiState.spectrumBinCount) {
        val row = uiState.latestSpectrumRow
        val width = uiState.spectrumBinCount

        if (row != null && width != null && width > 0 && row.size == width) {
            val height = 256
            val halfWidth = width / 2
            val unwrappedRow = ByteArray(width)
            var rowMin = 255
            var rowMax = 0

            for (x in 0 until width) {
                val unwrappedIndex = (x + halfWidth) % width
                val value = row[unwrappedIndex]
                unwrappedRow[x] = value
                val normalized = value.toInt() and 0xFF
                if (normalized < rowMin) {
                    rowMin = normalized
                }
                if (normalized > rowMax) {
                    rowMax = normalized
                }
            }

            val rowRange = rowMax - rowMin

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
                val rawValue = unwrappedRow[x].toInt() and 0xFF
                val normalizedValue = if (rowRange > 0) {
                    ((rawValue - rowMin) * 255) / rowRange
                } else {
                    rawValue
                }
                val color = waterfallPalette.colorAt(normalizedValue / 255f)
                oldPixels[x] = android.graphics.Color.argb(255, color.red, color.green, color.blue)
            }

            targetBitmap.setPixels(oldPixels, 0, width, 0, 0, width, height)
            waterfallBitmap = targetBitmap
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (telemetryExpanded) "Hide debug" else "Show debug",
                modifier = Modifier.clickable { telemetryExpanded = !telemetryExpanded },
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (telemetryExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = compactHeader,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Selected: $selectedFrequencyText Hz | Tapped: ${tappedFrequencyHz ?: "-"} | Hover: ${hoverFrequencyHz ?: "-"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Filter: ${uiState.bandwidthLowHz}/${uiState.bandwidthHighHz} Hz | Step: $tuningStepLabel | dC: ${audioMinusSpectrumCenterHz ?: "-"} Hz | Status: $shortStatus",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (waterfallVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color(0xFF102030)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = waterfallBitmap
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Waterfall",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                } else {
                    Text(
                        text = "Waterfall placeholder",
                        color = Color.White
                    )
                }

                if (isValidAxisRange(spectrumStartFreq, spectrumEndFreq)) {
                    val axisStartFreq = spectrumStartFreq!!
                    val axisEndFreq = spectrumEndFreq!!
                    val axisVisibleRange = axisEndFreq - axisStartFreq
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Color(0x66102030))
                            .clipToBounds()
                    ) {
                        val axisWidthDp = maxWidth.value
                        if (axisWidthDp > 0f && axisWidthDp.isFinite()) {
                            val visibleRangeHz = axisVisibleRange.roundToLong().coerceAtLeast(1L)
                            val majorTickStepHz = selectAxisMajorTickStepHz(
                                visibleRangeHz = visibleRangeHz,
                                availableWidthDp = axisWidthDp
                            )
                            val minorTickStepHz = selectAxisMinorTickStepHz(majorTickStepHz)
                            val axisTicks = buildAxisTicks(
                                startHz = axisStartFreq,
                                endHz = axisEndFreq,
                                majorTickStepHz = majorTickStepHz,
                                minorTickStepHz = minorTickStepHz
                            )
                            val displayTicks = axisTicks
                            val labelWidth = 64.dp
                            val labelTopOffset = 10.dp
                            val labeledTicks = buildList {
                                var previousRightDp = (-labelWidth).value

                                displayTicks.forEach { tick ->
                                    if (!tick.isMajor) {
                                        return@forEach
                                    }

                                    val ratio = ((tick.frequencyHz - axisStartFreq) / axisVisibleRange).toFloat()
                                    val tickOffset = axisWidthDp * ratio.coerceIn(0f, 1f)
                                    val labelLeftDp = (tickOffset - (labelWidth.value / 2f)).coerceIn(0f, axisWidthDp - labelWidth.value)
                                    val labelRightDp = labelLeftDp + labelWidth.value

                                    if (labelLeftDp >= previousRightDp + 4f) {
                                        add(tick.frequencyHz)
                                        previousRightDp = labelRightDp
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.70f))
                            )

                            displayTicks.forEach { tick ->
                                val ratio = ((tick.frequencyHz - axisStartFreq) / axisVisibleRange).toFloat()
                                val tickOffset = maxWidth * ratio.coerceIn(0f, 1f)
                                val centeredLabelOffset = tickOffset - (labelWidth / 2)
                                val labelOffset = when {
                                    centeredLabelOffset < 0.dp -> 0.dp
                                    centeredLabelOffset > maxWidth - labelWidth -> maxWidth - labelWidth
                                    else -> centeredLabelOffset
                                }
                                val shouldShowLabel = tick.isMajor && labeledTicks.contains(tick.frequencyHz)

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = tickOffset - (1.dp / 2))
                                        .width(1.dp)
                                        .height(if (tick.isMajor) 9.dp else 5.dp)
                                        .background(Color.White.copy(alpha = if (tick.isMajor) 0.85f else 0.55f))
                                )

                                if (shouldShowLabel) {
                                    Text(
                                        text = formatAxisTickLabel(
                                            frequencyHz = tick.frequencyHz,
                                            majorTickStepHz = majorTickStepHz
                                        ),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = labelOffset)
                                            .offset(y = labelTopOffset)
                                            .width(labelWidth),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(Color.White.copy(alpha = 0.45f))
                            )
                        }
                    }
                }

                if (tunedFrequencyRatio != null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                            .pointerInput(spectrumStartFreq, spectrumEndFreq) {
                                awaitEachGesture {
                                    val start = spectrumStartFreq ?: return@awaitEachGesture
                                    val end = spectrumEndFreq ?: return@awaitEachGesture
                                    val widthPx = size.width.toFloat()
                                    if (widthPx <= 0f) {
                                        return@awaitEachGesture
                                    }

                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    hoverFrequencyHz =
                                        (start + (down.position.x / widthPx).coerceIn(0f, 1f) * (end - start)).toLong()

                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change != null && change.pressed) {
                                            hoverFrequencyHz =
                                                (start + (change.position.x / widthPx).coerceIn(0f, 1f) * (end - start)).toLong()
                                        }
                                    } while (change != null && change.pressed)

                                    hoverFrequencyHz = null
                                }
                            }
                            .pointerInput(spectrumStartFreq, spectrumEndFreq) {
                                detectTapGestures { offset ->
                                    val start = spectrumStartFreq ?: return@detectTapGestures
                                    val end = spectrumEndFreq ?: return@detectTapGestures
                                    val widthPx = size.width.toFloat()
                                    if (widthPx <= 0f) {
                                        return@detectTapGestures
                                    }

                                    val ratio = (offset.x / widthPx).coerceIn(0f, 1f)
                                    val tappedHz = (start + ratio * (end - start)).toLong()
                                    tappedFrequencyHz = tappedHz
                                    viewModel.tune(tappedHz)
                                }
                            }
                    ) {
                        if (hoverFrequencyRatio != null) {
                            val hoverOffset = maxWidth * hoverFrequencyRatio.toFloat()
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = hoverOffset - (1.dp / 2))
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(Color(0x66B8E1FF))
                            )
                        }

                        val cursorOffset = maxWidth * tunedFrequencyRatio.toFloat()
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = cursorOffset - (1.dp / 2))
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color(0xCCFF6B6B))
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18222D))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactControlChip(
                        label = "<",
                        onClick = { viewModel.tune(uiState.frequencyHz - tuningStepHz) }
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedFrequencyText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                        Text(
                            text = " ${uiState.mode.wireValue.uppercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    CompactControlChip(
                        label = ">",
                        onClick = { viewModel.tune(uiState.frequencyHz + tuningStepHz) }
                    )

                    Box {
                        CompactControlChip(
                            label = tuningStepLabel,
                            onClick = { tuningStepMenuExpanded = true },
                            modifier = Modifier.defaultMinSize(minWidth = 60.dp)
                        )
                        DropdownMenu(
                            expanded = tuningStepMenuExpanded,
                            onDismissRequest = { tuningStepMenuExpanded = false }
                        ) {
                            tuningStepsHz.forEach { step ->
                                val stepLabel = when (step) {
                                    1_000L -> "1k"
                                    5_000L -> "5k"
                                    10_000L -> "10k"
                                    else -> step.toString()
                                }
                                DropdownMenuItem(
                                    text = { Text(stepLabel) },
                                    onClick = {
                                        tuningStepHz = step
                                        tuningStepMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactControlChip(
                    label = "PWR",
                    onClick = { viewModel.connect() },
                    modifier = Modifier.weight(1f)
                )
                CompactControlChip(
                    label = "-",
                    onClick = { viewModel.zoomOutSpectrum() },
                    modifier = Modifier.weight(1f)
                )
                CompactControlChip(
                    label = "+",
                    onClick = { viewModel.zoomInSpectrum() },
                    modifier = Modifier.weight(1f)
                )
                CompactControlChip(
                    label = "MAX",
                    onClick = { viewModel.zoomMaxSpectrum() },
                    modifier = Modifier.weight(1f)
                )
                CompactControlChip(
                    label = "C",
                    onClick = { viewModel.centerSpectrumOnTargetFrequency() },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        RadioControls(
            onConnect = { viewModel.connect() },
            currentMode = uiState.mode,
            onModeSelected = { mode: RadioMode -> viewModel.changeMode(mode) },
            audioVolume = uiState.audioVolume,
            audioMuted = uiState.audioMuted,
            onAudioVolumeChanged = { volume -> viewModel.setAudioVolume(volume) },
            onToggleMute = { viewModel.toggleMute() },
            showConnectButton = false
        )

        if (bandButtons.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bandButtons.chunked(5).forEach { rowBands ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowBands.forEach { (shortLabel, band) ->
                            val isActive = uiState.frequencyHz in band.start..band.end
                            CompactControlChip(
                                label = shortLabel,
                                onClick = { viewModel.selectBand(band.label) },
                                modifier = Modifier.weight(1f),
                                active = isActive
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactControlChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Box(
        modifier = modifier
            .background(if (active) Color(0xFF3A4C60) else Color(0xFF243241))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun selectAxisMajorTickStepHz(
    visibleRangeHz: Long,
    availableWidthDp: Float
): Long {
    val targetTickCount = (availableWidthDp / 96f).coerceIn(3f, 8f)
    val minimumStepHz = ceil(visibleRangeHz.toDouble() / targetTickCount.toDouble()).toLong().coerceAtLeast(1L)

    return AXIS_MAJOR_TICK_STEPS_HZ.firstOrNull { it >= minimumStepHz }
        ?: AXIS_MAJOR_TICK_STEPS_HZ.last()
}

private fun selectAxisMinorTickStepHz(majorTickStepHz: Long): Long {
    return when {
        majorTickStepHz % 5L == 0L -> (majorTickStepHz / 5L).coerceAtLeast(1L)
        majorTickStepHz % 2L == 0L -> (majorTickStepHz / 4L).coerceAtLeast(1L)
        else -> majorTickStepHz
    }
}

private fun buildAxisTicks(
    startHz: Double,
    endHz: Double,
    majorTickStepHz: Long,
    minorTickStepHz: Long
): List<AxisTick> {
    if (!startHz.isFinite() || !endHz.isFinite() || minorTickStepHz <= 0L || endHz <= startHz) {
        return emptyList()
    }

    val firstTickHz = ceil(startHz / minorTickStepHz).toLong() * minorTickStepHz
    val lastTickHz = endHz.toLong()
    val ticks = mutableListOf<AxisTick>()
    var tickHz = firstTickHz

    while (tickHz <= lastTickHz) {
        ticks += AxisTick(
            frequencyHz = tickHz.toDouble(),
            isMajor = tickHz % majorTickStepHz == 0L
        )
        tickHz += minorTickStepHz
    }

    return ticks
}

private fun isValidAxisRange(
    startHz: Double?,
    endHz: Double?
): Boolean {
    if (startHz == null || endHz == null) {
        return false
    }

    val visibleRangeHz = endHz - startHz
    return startHz.isFinite() &&
        endHz.isFinite() &&
        visibleRangeHz.isFinite() &&
        visibleRangeHz > 0.0
}

private fun formatAxisTickLabel(
    frequencyHz: Double,
    majorTickStepHz: Long
): String {
    val decimalsInMHz = when {
        majorTickStepHz >= 1_000_000L -> 1
        majorTickStepHz >= 500_000L -> 1
        majorTickStepHz >= 100_000L -> 2
        else -> 3
    }

    return when {
        frequencyHz >= 1_000_000.0 -> String.format(Locale.US, "%.${decimalsInMHz}fM", frequencyHz / 1_000_000.0)
        frequencyHz >= 1_000.0 -> String.format(Locale.US, "%.0fk", frequencyHz / 1_000.0)
        else -> String.format(Locale.US, "%.0f", frequencyHz)
    }
}

private data class AxisTick(
    val frequencyHz: Double,
    val isMajor: Boolean
)

private val AXIS_MAJOR_TICK_STEPS_HZ = listOf(
    1_000L,
    2_000L,
    5_000L,
    10_000L,
    20_000L,
    50_000L,
    100_000L,
    200_000L,
    500_000L,
    1_000_000L,
    2_000_000L,
    5_000_000L
)

private data class WaterfallRgb(
    val red: Int,
    val green: Int,
    val blue: Int
)

private enum class WaterfallPalette(
    val label: String,
    private val stops: List<WaterfallRgb>
) {
    Viridis(
        label = "Viridis",
        stops = listOf(
            WaterfallRgb(68, 1, 84),
            WaterfallRgb(59, 82, 139),
            WaterfallRgb(33, 145, 140),
            WaterfallRgb(94, 201, 98),
            WaterfallRgb(253, 231, 37)
        )
    ),
    Plasma(
        label = "Plasma",
        stops = listOf(
            WaterfallRgb(13, 8, 135),
            WaterfallRgb(126, 3, 168),
            WaterfallRgb(204, 71, 120),
            WaterfallRgb(248, 149, 64),
            WaterfallRgb(240, 249, 33)
        )
    ),
    Jet(
        label = "Jet",
        stops = listOf(
            WaterfallRgb(0, 0, 143),
            WaterfallRgb(0, 0, 255),
            WaterfallRgb(0, 255, 255),
            WaterfallRgb(255, 255, 0),
            WaterfallRgb(255, 0, 0),
            WaterfallRgb(128, 0, 0)
        )
    );

    fun colorAt(normalized: Float): WaterfallRgb {
        val clamped = normalized.coerceIn(0f, 1f)
        val segments = stops.size - 1
        val segment = minOf((clamped * segments).toInt(), segments - 1)
        val localT = (clamped * segments) - segment
        val start = stops[segment]
        val end = stops[segment + 1]

        return WaterfallRgb(
            red = (start.red + (end.red - start.red) * localT).toInt(),
            green = (start.green + (end.green - start.green) * localT).toInt(),
            blue = (start.blue + (end.blue - start.blue) * localT).toInt()
        )
    }
}
