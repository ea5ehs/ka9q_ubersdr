package es.niceto.ubersdr.ui.radio

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
    val minValidFrequencyHz = 10_000L
    val maxValidFrequencyHz = 30_000_000L
    val tuningStepsHz = listOf(10L, 100L, 1_000L, 5_000L, 10_000L)
    var waterfallPalette by remember { mutableStateOf(WaterfallPalette.Jet) }
    val bandButtonOrder = listOf("160", "80", "60", "40", "30", "20", "17", "15", "12", "10")
    var tuningStepHz by remember { mutableStateOf(1_000L) }
    var waterfallVisible by remember { mutableStateOf(true) }
    var topMenuExpanded by remember { mutableStateOf(false) }
    var tuningStepMenuExpanded by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val frequencyFocusRequester = remember { FocusRequester() }
    var waterfallBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tappedFrequencyHz by remember { mutableStateOf<Long?>(null) }
    var hoverFrequencyHz by remember { mutableStateOf<Long?>(null) }
    var editingFrequency by remember { mutableStateOf(false) }
    var editingFrequencyText by remember { mutableStateOf(uiState.frequencyHz.toString()) }
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
    val applyEditedFrequency = {
        val parsedFrequencyHz = editingFrequencyText
            .filter { it.isDigit() }
            .takeIf { it.isNotBlank() }
            ?.toLongOrNull()

        if (parsedFrequencyHz != null) {
            viewModel.tune(parsedFrequencyHz.coerceIn(minValidFrequencyHz, maxValidFrequencyHz))
            editingFrequency = false
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(uiState.frequencyHz, editingFrequency) {
        if (!editingFrequency) {
            editingFrequencyText = uiState.frequencyHz.toString()
        }
    }

    LaunchedEffect(editingFrequency) {
        if (editingFrequency) {
            editingFrequencyText = uiState.frequencyHz.toString()
            frequencyFocusRequester.requestFocus()
            keyboardController?.show()
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
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(Color(0xFF18222D))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(
                    onClick = { topMenuExpanded = true },
                    modifier = Modifier.height(28.dp)
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
                        text = "Filter: ${uiState.bandwidthLowHz}/${uiState.bandwidthHighHz} Hz | Step: $tuningStepLabel | dC: ${audioMinusSpectrumCenterHz ?: "-"} Hz | Status: $shortStatus",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Mas opciones proximamente",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            IconButton(
                onClick = viewModel::togglePower,
                modifier = Modifier.height(35.dp)
            ) {
                Icon(
                    imageVector = PowerIcon,
                    contentDescription = "Power",
                    modifier = Modifier.height(25.dp),
                    tint = if (uiState.isConnected) Color(0xFF5EDC6A) else Color(0xFFE35B5B)
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "UberSDR Android App v0.4",
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
                                        tappedFrequencyHz = tappedHz
                                        viewModel.tune(tappedHz)
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
                        if (editingFrequency) {
                            BasicTextField(
                                value = editingFrequencyText,
                                onValueChange = { updatedValue ->
                                    editingFrequencyText = updatedValue.filter { it.isDigit() }
                                },
                                modifier = Modifier
                                    .focusRequester(frequencyFocusRequester),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                    fontWeight = MaterialTheme.typography.headlineSmall.fontWeight,
                                    textAlign = TextAlign.Center
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { applyEditedFrequency() }
                                ),
                                cursorBrush = SolidColor(Color.White)
                            )
                        } else {
                            Text(
                                text = selectedFrequencyText,
                                modifier = Modifier.clickable {
                                    editingFrequency = true
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
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
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    RadioMode.USB to "USB",
                    RadioMode.LSB to "LSB",
                    RadioMode.AM to "AM",
                    RadioMode.CWU to "CWU"
                ).forEach { (mode, label) ->
                    val active = uiState.mode == mode
                    Button(
                        onClick = { viewModel.changeMode(mode) },
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
                        }
                    ) {
                        Text(label)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactControlChip(
                    label = "MIN",
                    onClick = { viewModel.zoomMinSpectrum() },
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

            if (isSsbMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18222D))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Passband ${ssbPassbandRange.start.roundToLong()}-${ssbPassbandRange.endInclusive.roundToLong()} Hz",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f)
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
                        valueRange = 0f..4000f
                    )
                }
            } else if (isCwMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18222D))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "CW width $cwWidthHz Hz",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
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
                        steps = 8
                    )
                }
            }
        }

        RadioControls(
            audioVolume = uiState.audioVolume,
            audioMuted = uiState.audioMuted,
            onAudioVolumeChanged = { volume -> viewModel.setAudioVolume(volume) },
            onToggleMute = { viewModel.toggleMute() }
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
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
