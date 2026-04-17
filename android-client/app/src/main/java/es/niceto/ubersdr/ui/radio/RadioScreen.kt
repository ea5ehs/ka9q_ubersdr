package es.niceto.ubersdr.ui.radio

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import es.niceto.ubersdr.model.RadioMode
import es.niceto.ubersdr.presentation.radio.RadioViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val CW_SPEC_BUFFER_CAPACITY = 10

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    val minValidFrequencyHz = 10_000L
    val maxValidFrequencyHz = 30_000_000L
    val tuningStepsHz = listOf(10L, 100L, 500L, 1_000L, 5_000L, 10_000L)
    var waterfallPalette by remember { mutableStateOf(WaterfallPalette.Jet) }
    val bandButtonOrder = listOf("160", "80", "60", "40", "30", "20", "17", "15", "12", "10")
    var waterfallVisible by remember { mutableStateOf(true) }
    var topMenuExpanded by remember { mutableStateOf(false) }
    var tuningStepMenuExpanded by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val frequencyFocusRequester = remember { FocusRequester() }
    var waterfallBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tappedFrequencyHz by remember { mutableStateOf<Long?>(null) }
    var hoverFrequencyHz by remember { mutableStateOf<Long?>(null) }
    var showFrequencyEditDialog by remember { mutableStateOf(false) }
    var frequencyEditText by remember {
        mutableStateOf(TextFieldValue(formatEditableFrequencyKhz(uiState.frequencyHz)))
    }
    val cwSpectrumBuffer = remember { mutableStateListOf<ByteArray>() }
    var cwSpectrumBufferKey by remember { mutableStateOf<CwSpectrumBufferKey?>(null) }
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
    val visualPassbandLowOffsetHz: Int
    val visualPassbandHighOffsetHz: Int
    val isCwMode = uiState.mode.wireValue.startsWith("cw")
    val isSsbMode = uiState.mode == RadioMode.USB || uiState.mode == RadioMode.LSB
    val useDashedPassbandLimitsOnly = isCwMode ||
        uiState.mode == RadioMode.USB ||
        uiState.mode == RadioMode.LSB
    if (isCwMode) {
        val passbandWidthHz = kotlin.math.abs(uiState.bandwidthHighHz - uiState.bandwidthLowHz)
        val halfPassbandWidthHz = passbandWidthHz / 2
        visualPassbandLowOffsetHz = -halfPassbandWidthHz
        visualPassbandHighOffsetHz = halfPassbandWidthHz
    } else {
        visualPassbandLowOffsetHz = uiState.bandwidthLowHz
        visualPassbandHighOffsetHz = uiState.bandwidthHighHz
    }
    val passbandLowFrequencyHz = uiState.frequencyHz + visualPassbandLowOffsetHz
    val passbandHighFrequencyHz = uiState.frequencyHz + visualPassbandHighOffsetHz
    val passbandStartFrequencyHz = minOf(passbandLowFrequencyHz, passbandHighFrequencyHz).toDouble()
    val passbandEndFrequencyHz = maxOf(passbandLowFrequencyHz, passbandHighFrequencyHz).toDouble()
    val accumulatedCwSpectrumRow = if (isCwMode) {
        accumulateSpectrumRows(
            visualSpectrumRows = cwSpectrumBuffer,
            activeN = uiState.cwAutoTuneAveraging
        )
    } else {
        null
    }
    val cwRfCandidate = if (isCwMode) {
        findCwRfCandidate(
            visualSpectrumRow = accumulatedCwSpectrumRow,
            binCount = uiState.spectrumBinCount,
            centerFreqHz = centerFreq,
            totalBandwidthHz = totalBandwidthHz,
            windowStartHz = passbandStartFrequencyHz,
            windowEndHz = passbandEndFrequencyHz,
            activeN = uiState.cwAutoTuneAveraging
        )
    } else {
        null
    }
    val validCwRfCandidateHz = cwRfCandidate
        ?.candidateFrequencyHz
        ?.takeIf { candidateHz ->
            candidateHz in minValidFrequencyHz..maxValidFrequencyHz &&
                candidateHz.toDouble() in passbandStartFrequencyHz..passbandEndFrequencyHz
        }
    val passbandStartRatio = if (spectrumStartFreq != null && spectrumEndFreq != null) {
        ((passbandStartFrequencyHz - spectrumStartFreq) / (spectrumEndFreq - spectrumStartFreq))
            .coerceIn(0.0, 1.0)
    } else {
        null
    }
    val passbandEndRatio = if (spectrumStartFreq != null && spectrumEndFreq != null) {
        ((passbandEndFrequencyHz - spectrumStartFreq) / (spectrumEndFreq - spectrumStartFreq))
            .coerceIn(0.0, 1.0)
    } else {
        null
    }
    val passbandVisible = passbandStartRatio != null &&
        passbandEndRatio != null &&
        passbandEndFrequencyHz > (spectrumStartFreq ?: Double.POSITIVE_INFINITY) &&
        passbandStartFrequencyHz < (spectrumEndFreq ?: Double.NEGATIVE_INFINITY)
    val ssbPassbandRange = when (uiState.mode) {
        RadioMode.USB -> uiState.bandwidthLowHz.coerceIn(0, 4000).toFloat()..
            uiState.bandwidthHighHz.coerceIn(0, 4000).toFloat()
        RadioMode.LSB -> kotlin.math.abs(uiState.bandwidthHighHz).coerceIn(0, 4000).toFloat()..
            kotlin.math.abs(uiState.bandwidthLowHz).coerceIn(0, 4000).toFloat()
        else -> 150f..2700f
    }
    val cwWidthHz = kotlin.math.abs(uiState.bandwidthHighHz - uiState.bandwidthLowHz)
        .coerceIn(100, 1000)
    val selectedFrequencyText = NumberFormat.getNumberInstance(Locale.US).format(uiState.frequencyHz)
    var currentDragFrequencyHz by remember { mutableStateOf<Long?>(null) }
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
    val tuningStepLabel = when (uiState.tuningStepHz) {
        1_000L -> "1 kHz"
        5_000L -> "5 kHz"
        10_000L -> "10 kHz"
        else -> "${uiState.tuningStepHz} Hz"
    }
    val decrementInteractionSource = remember { MutableInteractionSource() }
    val incrementInteractionSource = remember { MutableInteractionSource() }
    val decrementPressed by decrementInteractionSource.collectIsPressedAsState()
    val incrementPressed by incrementInteractionSource.collectIsPressedAsState()
    val latestFrequencyHz by rememberUpdatedState(uiState.frequencyHz)
    val latestTuningStepHz by rememberUpdatedState(uiState.tuningStepHz)
    val bandButtons = bandButtonOrder.mapNotNull { shortLabel ->
        uiState.availableBands.firstOrNull { it.label == "${shortLabel}m" }?.let { band ->
            shortLabel to band
        }
    }
    val applyEditedFrequency = {
        val parsedFrequencyHz = parseEditableFrequencyKhzToHz(frequencyEditText.text)

        if (parsedFrequencyHz != null && parsedFrequencyHz in minValidFrequencyHz..maxValidFrequencyHz) {
            viewModel.tune(parsedFrequencyHz)
            showFrequencyEditDialog = false
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(uiState.frequencyHz, showFrequencyEditDialog) {
        if (!showFrequencyEditDialog) {
            frequencyEditText = TextFieldValue(formatEditableFrequencyKhz(uiState.frequencyHz))
        }
    }

    LaunchedEffect(showFrequencyEditDialog) {
        if (showFrequencyEditDialog) {
            val currentText = formatEditableFrequencyKhz(uiState.frequencyHz)
            frequencyEditText = TextFieldValue(
                text = currentText,
                selection = TextRange(currentText.length)
            )
            keyboardController?.show()
        }
    }

    LaunchedEffect(uiState.keepScreenOn) {
        view.keepScreenOn = uiState.keepScreenOn
    }

    LaunchedEffect(decrementPressed) {
        if (decrementPressed) {
            delay(333L)
            while (true) {
                viewModel.tune((latestFrequencyHz - latestTuningStepHz).coerceIn(minValidFrequencyHz, maxValidFrequencyHz))
                delay(333L)
            }
        }
    }

    LaunchedEffect(incrementPressed) {
        if (incrementPressed) {
            delay(333L)
            while (true) {
                viewModel.tune((latestFrequencyHz + latestTuningStepHz).coerceIn(minValidFrequencyHz, maxValidFrequencyHz))
                delay(333L)
            }
        }
    }

    LaunchedEffect(
        uiState.latestSpectrumRow,
        uiState.spectrumBinCount,
        uiState.spectrumCenterFreqHz,
        uiState.spectrumTotalBandwidthHz
    ) {
        val row = uiState.latestSpectrumRow
        val width = uiState.spectrumBinCount
        val currentCenterFreq = uiState.spectrumCenterFreqHz
        val currentTotalBandwidthHz = uiState.spectrumTotalBandwidthHz

        if (
            row != null &&
            width != null &&
            width > 0 &&
            row.size == width &&
            currentCenterFreq != null &&
            currentTotalBandwidthHz != null &&
            currentTotalBandwidthHz.isFinite() &&
            currentTotalBandwidthHz > 0.0
        ) {
            val height = 256
            val visualSpectrumRow = unwrapSpectrumRow(
                rawSpectrumRow = row,
                binCount = width
            ) ?: return@LaunchedEffect
            val currentBufferKey = CwSpectrumBufferKey(
                binCount = width,
                centerFreqHz = currentCenterFreq,
                totalBandwidthHz = currentTotalBandwidthHz
            )

            if (cwSpectrumBufferKey != currentBufferKey) {
                cwSpectrumBuffer.clear()
                cwSpectrumBufferKey = currentBufferKey
            }
            cwSpectrumBuffer.add(visualSpectrumRow)
            while (cwSpectrumBuffer.size > CW_SPEC_BUFFER_CAPACITY) {
                cwSpectrumBuffer.removeAt(0)
            }

            var rowMin = 255
            var rowMax = 0
            for (x in 0 until width) {
                val value = visualSpectrumRow[x]
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
                val rawValue = visualSpectrumRow[x].toInt() and 0xFF
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val isCompactLayout = maxWidth <= 380.dp || maxHeight <= 760.dp
        val isRoomyHeight = maxHeight >= 900.dp
        val isLowHeightLayout = maxHeight <= 760.dp
        val screenHorizontalPadding = if (isCompactLayout) 10.dp else 16.dp
        val screenTopPadding = if (isCompactLayout) 6.dp else 12.dp
        val screenBottomPadding = if (isCompactLayout) 8.dp else 16.dp
        val sectionSpacing = when {
            isCompactLayout -> 6.dp
            isRoomyHeight -> 14.dp
            else -> 12.dp
        }
        val controlSpacing = if (isCompactLayout) 6.dp else 8.dp
        val toolRowSpacing = when {
            isCompactLayout -> 0.dp
            isRoomyHeight -> 10.dp
            else -> 6.dp
        }
        val compactChipPadding = if (isCompactLayout) {
            PaddingValues(horizontal = 6.dp, vertical = 5.dp)
        } else {
            PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        }
        val tuneBarPadding = if (isCompactLayout) {
            PaddingValues(horizontal = 8.dp, vertical = 5.dp)
        } else {
            PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        }
        val filterPanelPadding = if (isCompactLayout) {
            PaddingValues(horizontal = 8.dp, vertical = 3.dp)
        } else {
            PaddingValues(horizontal = 10.dp, vertical = 5.dp)
        }
        val modeButtonPadding = if (isCompactLayout) {
            PaddingValues(horizontal = 6.dp, vertical = 5.dp)
        } else {
            PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        }
        val waterfallHeight = when {
            isLowHeightLayout -> 208.dp
            isRoomyHeight -> 272.dp
            else -> 240.dp
        }
        val topBarHeight = if (isCompactLayout) 30.dp else 32.dp
        val topMenuIconHeight = if (isCompactLayout) 26.dp else 28.dp
        val powerButtonHeight = if (isCompactLayout) 32.dp else 35.dp
        val powerIconHeight = if (isCompactLayout) 22.dp else 25.dp
        val sliderHeight = if (isCompactLayout) 18.dp else 20.dp
        val sliderScaleY = if (isCompactLayout) 0.42f else 0.48f
        val tuningStepChipLabel = when (uiState.tuningStepHz) {
            1_000L -> if (isCompactLayout) "1k" else "1 kHz"
            5_000L -> if (isCompactLayout) "5k" else "5 kHz"
            10_000L -> if (isCompactLayout) "10k" else "10 kHz"
            else -> if (isCompactLayout) uiState.tuningStepHz.toString() else "${uiState.tuningStepHz} Hz"
        }
        val frequencyDisplayText = formatFrequencyKhzSpanish(uiState.frequencyHz)
        val frequencyDisplayTint = Color(0xFFA7FFB7)
        val frequencySecondaryTint = frequencyDisplayTint.copy(alpha = 0.72f)
        val frequencyDisplayMinHeight = if (isCompactLayout) 34.dp else 38.dp
        val stepChipMinWidth = if (isCompactLayout) 42.dp else 48.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = screenHorizontalPadding,
                    top = screenTopPadding,
                    end = screenHorizontalPadding,
                    bottom = screenBottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight)
                .background(Color(0xFF18222D))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(
                    onClick = { topMenuExpanded = true },
                    modifier = Modifier.height(topMenuIconHeight)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Menu",
                        tint = Color.White.copy(alpha = 0.92f)
                    )
                }

                DropdownMenu(
                    expanded = topMenuExpanded,
                    onDismissRequest = { topMenuExpanded = false }
                ) {
                    Text(
                        text = "Waterfall",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                    WaterfallPalette.values().forEach { palette ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = palette.label + if (palette == waterfallPalette) " \u2713" else ""
                                )
                            },
                            onClick = {
                                waterfallPalette = palette
                                topMenuExpanded = false
                            }
                        )
                    }
                    Text(
                        text = "Telemetria",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = compactHeader,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Selected: $selectedFrequencyText Hz | Tapped: ${tappedFrequencyHz ?: "-"} | Hover: ${hoverFrequencyHz ?: "-"}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "CW SPEC N: ${uiState.cwAutoTuneAveraging} | Buffer: ${cwSpectrumBuffer.size}/$CW_SPEC_BUFFER_CAPACITY",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Ajustes avanzados",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "CW AutoTune Averaging",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = uiState.cwAutoTuneAveraging.toFloat(),
                        onValueChange = { updatedN ->
                            viewModel.setCwAutoTuneAveraging(
                                updatedN.roundToInt().coerceIn(1, CW_SPEC_BUFFER_CAPACITY)
                            )
                        },
                        valueRange = 1f..CW_SPEC_BUFFER_CAPACITY.toFloat(),
                        steps = CW_SPEC_BUFFER_CAPACITY - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(20.dp)
                            .graphicsLayer(scaleY = 0.48f)
                    )
                    Text(
                        text = if (cwRfCandidate != null) {
                            "CW Lab: N=${cwRfCandidate.activeN} | ${cwRfCandidate.candidateFrequencyHz} Hz | peak ${cwRfCandidate.peakBin} | value ${cwRfCandidate.peakValue} | bins ${cwRfCandidate.startBin}-${cwRfCandidate.endBin}"
                        } else {
                            "CW Lab: -"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Filter: ${uiState.bandwidthLowHz}/${uiState.bandwidthHighHz} Hz | Step: $tuningStepLabel | dC: ${audioMinusSpectrumCenterHz ?: "-"} Hz | Status: $shortStatus",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Mas opciones proximamente",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Pantalla",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Mantener pantalla activa",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Puede aumentar el consumo de bateria",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        trailingIcon = {
                            Switch(
                                checked = uiState.keepScreenOn,
                                onCheckedChange = viewModel::setKeepScreenOn
                            )
                        },
                        onClick = {
                            viewModel.setKeepScreenOn(!uiState.keepScreenOn)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Restablecer ajustes guardados",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Borra frecuencia, modo, step, volumen, mute y pantalla activa",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            topMenuExpanded = false
                            showResetSettingsDialog = true
                        }
                    )
                }
            }

            IconButton(
                onClick = viewModel::togglePower,
                modifier = Modifier.height(powerButtonHeight)
            ) {
                Icon(
                    imageVector = PowerIcon,
                    contentDescription = "Power",
                    modifier = Modifier.height(powerIconHeight),
                    tint = if (uiState.isConnected) Color(0xFF5EDC6A) else Color(0xFFE35B5B)
                )
            }
        }

        if (waterfallVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(waterfallHeight)
                    .background(Color(0xFF102030))
                    .border(1.dp, Color.White.copy(alpha = 0.70f)),
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "UberSDR Android App beta 0.6",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Niceto Muñoz (EA5ZL) · 2026",
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (isValidAxisRange(spectrumStartFreq, spectrumEndFreq)) {
                        val axisStartFreq = spectrumStartFreq!!
                        val axisEndFreq = spectrumEndFreq!!
                        val axisVisibleRange = axisEndFreq - axisStartFreq
                        BoxWithConstraints(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .height(if (isCompactLayout) 24.dp else 28.dp)
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
                                val labelWidth = if (isCompactLayout) 56.dp else 64.dp
                                val labelTopOffset = if (isCompactLayout) 8.dp else 10.dp
                                val labeledTicks = buildList {
                                    var previousRightDp = (-labelWidth).value

                                    displayTicks.forEach { tick ->
                                        if (!tick.isMajor) {
                                            return@forEach
                                        }

                                        val ratio = ((tick.frequencyHz - axisStartFreq) / axisVisibleRange).toFloat()
                                        val tickOffset = axisWidthDp * ratio.coerceIn(0f, 1f)
                                        val labelLeftDp =
                                            (tickOffset - (labelWidth.value / 2f)).coerceIn(0f, axisWidthDp - labelWidth.value)
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
                                            .height(if (tick.isMajor) {
                                                if (isCompactLayout) 7.dp else 9.dp
                                            } else {
                                                if (isCompactLayout) 4.dp else 5.dp
                                            })
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

                    if (tunedFrequencyRatio != null) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset: Offset ->
                                            val start = spectrumStartFreq ?: return@detectHorizontalDragGestures
                                            val end = spectrumEndFreq ?: return@detectHorizontalDragGestures
                                            val widthPx = size.width.toFloat()
                                            if (widthPx <= 0f) {
                                                return@detectHorizontalDragGestures
                                            }

                                            hoverFrequencyHz =
                                                (start + (offset.x / widthPx).coerceIn(0f, 1f) * (end - start)).toLong()
                                            currentDragFrequencyHz = uiState.frequencyHz
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            val visibleFrequencyHz = currentDragFrequencyHz ?: uiState.frequencyHz
                                            val visibleBandwidthHz = totalBandwidthHz ?: return@detectHorizontalDragGestures
                                            val widthPx = size.width.toFloat()
                                            if (widthPx <= 0f || !visibleBandwidthHz.isFinite() || visibleBandwidthHz <= 0.0) {
                                                return@detectHorizontalDragGestures
                                            }

                                            val deltaHz = ((dragAmount / widthPx) * visibleBandwidthHz).roundToLong() * -1L
                                            if (deltaHz == 0L) {
                                                return@detectHorizontalDragGestures
                                            }

                                            val nextFrequencyHz = visibleFrequencyHz + deltaHz
                                            currentDragFrequencyHz = viewModel.dragTuneSpectrumTo(nextFrequencyHz)
                                        },
                                        onDragEnd = {
                                            hoverFrequencyHz = null
                                            currentDragFrequencyHz = null
                                        },
                                        onDragCancel = {
                                            hoverFrequencyHz = null
                                            currentDragFrequencyHz = null
                                        }
                                    )
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
                                        val roundedTappedHz = roundFrequencyToStep(tappedHz, uiState.tuningStepHz)
                                            .coerceIn(minValidFrequencyHz, maxValidFrequencyHz)
                                        tappedFrequencyHz = roundedTappedHz
                                        viewModel.tune(roundedTappedHz)
                                    }
                                }
                        ) {
                            if (passbandVisible) {
                                val passbandStartOffset = maxWidth * passbandStartRatio!!.toFloat()
                                val passbandEndOffset = maxWidth * passbandEndRatio!!.toFloat()
                                val passbandWidth = (passbandEndOffset - passbandStartOffset).coerceAtLeast(2.dp)

                                if (!useDashedPassbandLimitsOnly) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = passbandStartOffset)
                                            .fillMaxHeight()
                                            .width(passbandWidth)
                                            .background(Color(0x5558B8FF))
                                    )
                                }

                                if (useDashedPassbandLimitsOnly) {
                                    Canvas(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = passbandStartOffset - (1.dp / 2))
                                            .fillMaxHeight()
                                            .width(1.dp)
                                    ) {
                                        drawLine(
                                            color = Color(0xAA9FD8FF),
                                            start = Offset(x = size.width / 2f, y = 0f),
                                            end = Offset(x = size.width / 2f, y = size.height),
                                            strokeWidth = size.width,
                                            pathEffect = PathEffect.dashPathEffect(
                                                intervals = floatArrayOf(8f, 8f)
                                            )
                                        )
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = passbandEndOffset - (1.dp / 2))
                                            .fillMaxHeight()
                                            .width(1.dp)
                                    ) {
                                        drawLine(
                                            color = Color(0xAA9FD8FF),
                                            start = Offset(x = size.width / 2f, y = 0f),
                                            end = Offset(x = size.width / 2f, y = size.height),
                                            strokeWidth = size.width,
                                            pathEffect = PathEffect.dashPathEffect(
                                                intervals = floatArrayOf(8f, 8f)
                                            )
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = passbandStartOffset - (1.dp / 2))
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .background(Color(0xAA9FD8FF))
                                    )

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = passbandEndOffset - (1.dp / 2))
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .background(Color(0xAA9FD8FF))
                                    )
                                }
                            }

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
                        .padding(tuneBarPadding),
                    horizontalArrangement = Arrangement.spacedBy(controlSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactControlChip(
                        label = "<",
                        onClick = { viewModel.tune((uiState.frequencyHz - uiState.tuningStepHz).coerceIn(minValidFrequencyHz, maxValidFrequencyHz)) },
                        interactionSource = decrementInteractionSource,
                        contentPadding = compactChipPadding
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = frequencyDisplayMinHeight)
                                .background(Color(0xFF101A12))
                                .border(1.dp, frequencyDisplayTint.copy(alpha = 0.18f))
                                .padding(horizontal = if (isCompactLayout) 10.dp else 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = frequencyDisplayText,
                                modifier = Modifier.clickable {
                                    showFrequencyEditDialog = true
                                },
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = if (isCompactLayout) 0.5.sp else 0.8.sp
                                ),
                                color = frequencyDisplayTint,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                        Text(
                            text = " kHz",
                            style = MaterialTheme.typography.labelMedium,
                            color = frequencySecondaryTint,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    CompactControlChip(
                        label = ">",
                        onClick = { viewModel.tune((uiState.frequencyHz + uiState.tuningStepHz).coerceIn(minValidFrequencyHz, maxValidFrequencyHz)) },
                        interactionSource = incrementInteractionSource,
                        contentPadding = compactChipPadding
                    )

                    Box {
                        CompactControlChip(
                            label = tuningStepChipLabel,
                            onClick = { tuningStepMenuExpanded = true },
                            modifier = Modifier.defaultMinSize(minWidth = stepChipMinWidth),
                            contentPadding = compactChipPadding
                        )
                        DropdownMenu(
                            expanded = tuningStepMenuExpanded,
                            onDismissRequest = { tuningStepMenuExpanded = false }
                        ) {
                            tuningStepsHz.forEach { step ->
                                val stepLabel = when (step) {
                                    500L -> "500"
                                    1_000L -> "1k"
                                    5_000L -> "5k"
                                    10_000L -> "10k"
                                    else -> step.toString()
                                }
                                DropdownMenuItem(
                                    text = { Text(stepLabel) },
                                    onClick = {
                                        viewModel.setTuningStep(step)
                                        tuningStepMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(toolRowSpacing))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    controlSpacing,
                    Alignment.CenterHorizontally
                ),
                verticalArrangement = Arrangement.spacedBy(controlSpacing)
            ) {
                listOf(
                    RadioMode.USB to "USB",
                    RadioMode.LSB to "LSB",
                    RadioMode.AM to "AM",
                    RadioMode.CWU to if (uiState.mode == RadioMode.CWL) "CWL" else "CWU"
                ).forEach { (mode, label) ->
                    val active = if (mode == RadioMode.CWU) {
                        uiState.mode == RadioMode.CWU || uiState.mode == RadioMode.CWL
                    } else {
                        uiState.mode == mode
                    }
                    Button(
                        onClick = {
                            val targetMode = if (mode == RadioMode.CWU) {
                                when (uiState.mode) {
                                    RadioMode.CWU -> RadioMode.CWL
                                    RadioMode.CWL -> RadioMode.CWU
                                    else -> RadioMode.CWU
                                }
                            } else {
                                mode
                            }
                            viewModel.changeMode(targetMode)
                        },
                        colors = if (active) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        contentPadding = modeButtonPadding,
                        modifier = if (isCompactLayout) {
                            Modifier.defaultMinSize(minWidth = 60.dp)
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(toolRowSpacing))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(controlSpacing)
            ) {
                CompactControlChip(
                    label = "MIN",
                    onClick = { viewModel.zoomMinSpectrum() },
                    modifier = Modifier.weight(1f),
                    contentPadding = compactChipPadding
                )
                CompactControlChip(
                    label = "-",
                    onClick = { viewModel.zoomOutSpectrum() },
                    modifier = Modifier.weight(1f),
                    contentPadding = compactChipPadding
                )
                CompactControlChip(
                    label = "+",
                    onClick = { viewModel.zoomInSpectrum() },
                    modifier = Modifier.weight(1f),
                    contentPadding = compactChipPadding
                )
                CompactControlChip(
                    label = "MAX",
                    onClick = { viewModel.zoomMaxSpectrum() },
                    modifier = Modifier.weight(1f),
                    contentPadding = compactChipPadding
                )
                CompactControlChip(
                    label = "C",
                    onClick = { viewModel.centerSpectrumOnTargetFrequency() },
                    modifier = Modifier.weight(1f),
                    contentPadding = compactChipPadding
                )
            }

            if (isSsbMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                        .padding(filterPanelPadding),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = "Passband ${ssbPassbandRange.start.roundToLong()}-${ssbPassbandRange.endInclusive.roundToLong()} Hz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RangeSlider(
                        value = ssbPassbandRange,
                        onValueChange = { updatedRange ->
                            val lowerEdgeHz = updatedRange.start.roundToLong().coerceIn(0L, 3950L).toInt()
                            val upperEdgeHz = updatedRange.endInclusive
                                .roundToLong()
                                .coerceIn((lowerEdgeHz + 50).toLong(), 4000L)
                                .toInt()
                            if (uiState.mode == RadioMode.USB) {
                                viewModel.changeFilter(lowerEdgeHz, upperEdgeHz)
                            } else {
                                viewModel.changeFilter(-upperEdgeHz, -lowerEdgeHz)
                            }
                        },
                        valueRange = 0f..4000f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sliderHeight)
                            .graphicsLayer(scaleY = sliderScaleY)
                    )
                }
            } else if (isCwMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                        .padding(filterPanelPadding),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = "CW width $cwWidthHz Hz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(controlSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = cwWidthHz.toFloat(),
                            onValueChange = { updatedWidth ->
                                val snappedWidthHz = ((updatedWidth / 100f).roundToLong() * 100L)
                                    .coerceIn(100L, 1000L)
                                    .toInt()
                                val halfWidthHz = snappedWidthHz / 2
                                viewModel.changeFilter(-halfWidthHz, halfWidthHz)
                            },
                            valueRange = 100f..1000f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(sliderHeight)
                                .graphicsLayer(scaleY = sliderScaleY)
                        )
                        Button(
                            onClick = {
                                val candidateHz = validCwRfCandidateHz ?: return@Button
                                val binBandwidthHz = uiState.spectrumBinBandwidthHz
                                if (
                                    binBandwidthHz != null &&
                                    binBandwidthHz.isFinite() &&
                                    binBandwidthHz > 0.0 &&
                                    kotlin.math.abs(candidateHz - uiState.frequencyHz) < binBandwidthHz
                                ) {
                                    return@Button
                                }
                                viewModel.tune(candidateHz)
                            },
                            enabled = validCwRfCandidateHz != null,
                            contentPadding = if (isCompactLayout) {
                                PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            } else {
                                ButtonDefaults.ContentPadding
                            },
                            modifier = Modifier.defaultMinSize(minWidth = if (isCompactLayout) 36.dp else 40.dp)
                        ) {
                            Text("A")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(toolRowSpacing))
        }

        RadioControls(
            audioVolume = uiState.audioVolume,
            audioMuted = uiState.audioMuted,
            onAudioVolumeChanged = { volume -> viewModel.setAudioVolume(volume) },
            onToggleMute = { viewModel.toggleMute() },
            compact = isCompactLayout,
            modifier = Modifier
        )
        Spacer(modifier = Modifier.height(toolRowSpacing))

        if (bandButtons.isNotEmpty()) {
            val bandRowSpacing = when {
                isCompactLayout -> 4.dp
                isRoomyHeight -> 8.dp
                else -> 6.dp
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(bandRowSpacing)
            ) {
                bandButtons.chunked(5).forEach { rowBands ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(bandRowSpacing)
                    ) {
                        rowBands.forEach { (shortLabel, band) ->
                            val isActive = uiState.frequencyHz in band.start..band.end
                            CompactControlChip(
                                label = shortLabel,
                                onClick = { viewModel.selectBand(band.label) },
                                modifier = Modifier.weight(1f),
                                active = isActive,
                                contentPadding = compactChipPadding
                            )
                        }
                    }
                }
            }
        }

        if (showResetSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showResetSettingsDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetPersistedSettings()
                            showResetSettingsDialog = false
                        }
                    ) {
                        Text("Restablecer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetSettingsDialog = false }) {
                        Text("Cancelar")
                    }
                },
                title = { Text("Restablecer ajustes guardados") },
                text = {
                    Text("Se borraran los ajustes persistidos y se volvera a valores seguros por defecto.")
                }
            )
        }

        if (showFrequencyEditDialog) {
            AlertDialog(
                onDismissRequest = {
                    showFrequencyEditDialog = false
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                confirmButton = {
                    TextButton(onClick = { applyEditedFrequency() }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showFrequencyEditDialog = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ) {
                        Text("Cancelar")
                    }
                },
                title = { Text("Frecuencia") },
                text = {
                    TextField(
                        value = frequencyEditText,
                        onValueChange = { updatedValue ->
                            val normalizedText = buildString {
                                var separatorUsed = false
                                updatedValue.text.forEach { char ->
                                    when {
                                        char.isDigit() -> append(char)
                                        (char == '.' || char == ',') && !separatorUsed -> {
                                            append('.')
                                            separatorUsed = true
                                        }
                                    }
                                }
                            }
                            val clampedSelection = updatedValue.selection.end.coerceIn(0, normalizedText.length)
                            frequencyEditText = TextFieldValue(
                                text = normalizedText,
                                selection = TextRange(clampedSelection)
                            )
                        },
                        modifier = Modifier.focusRequester(frequencyFocusRequester),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { applyEditedFrequency() }
                        ),
                        suffix = {
                            Text("kHz")
                        }
                    )
                }
            )
        }
    }
    }
}

@Composable
private fun CompactControlChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(
                interactionSource = resolvedInteractionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun roundFrequencyToStep(frequencyHz: Long, stepHz: Long): Long {
    if (stepHz <= 0L) {
        return frequencyHz
    }
    return ((frequencyHz + (stepHz / 2L)) / stepHz) * stepHz
}

private fun formatFrequencyKhzSpanish(frequencyHz: Long): String {
    return formatFrequencyKhzSpanishFromRawHzDigits(frequencyHz.toString())
}

private fun formatEditableFrequencyKhz(frequencyHz: Long): String {
    val khz = frequencyHz / 1000L
    val fractionalHz = (frequencyHz % 1000L).toString().padStart(3, '0')
    return "$khz.$fractionalHz"
}

private fun parseEditableFrequencyKhzToHz(input: String): Long? {
    val normalized = input
        .trim()
        .replace(',', '.')
        .takeIf { it.isNotEmpty() }
        ?: return null
    val parts = normalized.split('.')
    if (parts.size > 2) {
        return null
    }

    val integerPart = parts[0].filter { it.isDigit() }.ifEmpty { "0" }
    val fractionalPart = parts.getOrNull(1)
        ?.filter { it.isDigit() }
        ?.take(3)
        ?.padEnd(3, '0')
        ?: "000"

    val khz = integerPart.toLongOrNull() ?: return null
    val hzFraction = fractionalPart.toLongOrNull() ?: return null
    return (khz * 1000L) + hzFraction
}

private fun formatFrequencyKhzSpanishFromRawHzDigits(rawHzDigits: String): String {
    if (rawHzDigits.isEmpty()) {
        return ""
    }

    val integerDigits = rawHzDigits.dropLast(3)
    val fractionalDigits = rawHzDigits.takeLast(3).padStart(3, '0')
    val integerPart = if (integerDigits.isEmpty()) {
        "0"
    } else {
        formatSpanishThousands(integerDigits)
    }

    return "$integerPart,$fractionalDigits"
}

private fun formatSpanishThousands(digits: String): String {
    if (digits.length <= 3) {
        return digits
    }

    val firstGroupLength = digits.length % 3
    val builder = StringBuilder()
    var index = 0

    if (firstGroupLength > 0) {
        builder.append(digits.substring(0, firstGroupLength))
        index = firstGroupLength
    }

    while (index < digits.length) {
        if (builder.isNotEmpty()) {
            builder.append('.')
        }
        builder.append(digits.substring(index, index + 3))
        index += 3
    }

    return builder.toString()
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

private data class CwRfCandidate(
    val activeN: Int,
    val startBin: Int,
    val endBin: Int,
    val peakBin: Int,
    val peakValue: Int,
    val candidateFrequencyHz: Long
)

private data class CwSpectrumBufferKey(
    val binCount: Int,
    val centerFreqHz: Long,
    val totalBandwidthHz: Double
)

private fun findCwRfCandidate(
    visualSpectrumRow: ByteArray?,
    binCount: Int?,
    centerFreqHz: Long?,
    totalBandwidthHz: Double?,
    windowStartHz: Double,
    windowEndHz: Double,
    activeN: Int
): CwRfCandidate? {
    if (
        visualSpectrumRow == null ||
        binCount == null ||
        centerFreqHz == null ||
        totalBandwidthHz == null ||
        binCount <= 0 ||
        visualSpectrumRow.size != binCount ||
        !totalBandwidthHz.isFinite() ||
        totalBandwidthHz <= 0.0
    ) {
        return null
    }

    val startFreqHz = centerFreqHz.toDouble() - totalBandwidthHz / 2.0
    val clampedWindowStartHz = windowStartHz.coerceAtLeast(startFreqHz)
    val clampedWindowEndHz = windowEndHz.coerceAtMost(startFreqHz + totalBandwidthHz)
    if (!clampedWindowStartHz.isFinite() || !clampedWindowEndHz.isFinite() || clampedWindowEndHz <= clampedWindowStartHz) {
        return null
    }

    val startRatio = ((clampedWindowStartHz - startFreqHz) / totalBandwidthHz).coerceIn(0.0, 1.0)
    val endRatio = ((clampedWindowEndHz - startFreqHz) / totalBandwidthHz).coerceIn(0.0, 1.0)
    var startBin = (startRatio * binCount).toInt().coerceIn(0, binCount - 1)
    var endBin = (endRatio * binCount).toInt().coerceIn(0, binCount - 1)
    if (startBin > endBin) {
        val swap = startBin
        startBin = endBin
        endBin = swap
    }

    var peakBin = startBin
    var peakValue = -1
    for (visualBin in startBin..endBin) {
        val value = visualSpectrumRow[visualBin].toInt() and 0xFF
        if (value > peakValue) {
            peakValue = value
            peakBin = visualBin
        }
    }

    if (peakValue < 0) {
        return null
    }

    val candidateFrequencyHz =
        (startFreqHz + (peakBin.toDouble() / binCount.toDouble()) * totalBandwidthHz).roundToLong()

    return CwRfCandidate(
        activeN = activeN,
        startBin = startBin,
        endBin = endBin,
        peakBin = peakBin,
        peakValue = peakValue,
        candidateFrequencyHz = candidateFrequencyHz
    )
}

private fun unwrapSpectrumRow(
    rawSpectrumRow: ByteArray,
    binCount: Int
): ByteArray? {
    if (binCount <= 0 || rawSpectrumRow.size != binCount) {
        return null
    }

    val halfWidth = binCount / 2
    val visualSpectrumRow = ByteArray(binCount)
    for (x in 0 until binCount) {
        val unwrappedIndex = (x + halfWidth) % binCount
        visualSpectrumRow[x] = rawSpectrumRow[unwrappedIndex]
    }
    return visualSpectrumRow
}

private fun accumulateSpectrumRows(
    visualSpectrumRows: List<ByteArray>,
    activeN: Int
): ByteArray? {
    val effectiveN = activeN.coerceIn(1, CW_SPEC_BUFFER_CAPACITY)
    if (visualSpectrumRows.isEmpty()) {
        return null
    }

    val rowsToUse = visualSpectrumRows.takeLast(effectiveN)
    if (rowsToUse.isEmpty()) {
        return null
    }

    val binCount = rowsToUse.first().size
    if (binCount <= 0 || rowsToUse.any { it.size != binCount }) {
        return null
    }

    val sums = IntArray(binCount)
    rowsToUse.forEach { row ->
        for (index in 0 until binCount) {
            sums[index] += row[index].toInt() and 0xFF
        }
    }

    return ByteArray(binCount) { index ->
        (sums[index] / rowsToUse.size).toByte()
    }
}

private data class AxisTick(
    val frequencyHz: Double,
    val isMajor: Boolean
)

private val PowerIcon: ImageVector = ImageVector.Builder(
    name = "PowerIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = null,
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.2f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(9f, 4.5f)
        arcTo(7.5f, 7.5f, 0f, false, false, 4.5f, 12f)
        arcTo(7.5f, 7.5f, 0f, false, false, 19.5f, 12f)
        arcTo(7.5f, 7.5f, 0f, false, false, 15f, 4.5f)
    }
    path(
        fill = null,
        stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
        strokeLineWidth = 2.2f,
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(12f, 3f)
        lineTo(12f, 10f)
    }
}.build()

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
