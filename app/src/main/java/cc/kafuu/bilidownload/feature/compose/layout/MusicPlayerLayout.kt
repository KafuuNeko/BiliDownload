package cc.kafuu.bilidownload.feature.compose.layout

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.audio.spectrum.MusicSpectrumBitmapTile
import cc.kafuu.bilidownload.common.audio.spectrum.MusicSpectrumData
import cc.kafuu.bilidownload.common.audio.spectrum.RealtimeSpectrumFrame
import cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer.MusicPlayerUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer.MusicPlayerUiState
import cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer.MusicSpectrumMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MusicPlayerLayout(
    state: MusicPlayerUiState,
    onIntent: (MusicPlayerUiIntent) -> Unit
) {
    when (state) {
        MusicPlayerUiState.None -> MusicLoadingView()
        is MusicPlayerUiState.Playing -> MusicPlayerContent(state, onIntent)
        is MusicPlayerUiState.Error -> MusicErrorView(state.message, onIntent)
    }
}

@Composable
private fun MusicLoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicBackground),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MusicPink)
    }
}

@Composable
private fun MusicErrorView(
    message: String,
    onIntent: (MusicPlayerUiIntent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
            IconButton(onClick = { onIntent(MusicPlayerUiIntent.GoBack) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_2),
                    contentDescription = stringResource(R.string.iv_back_content_description),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun MusicPlayerContent(
    state: MusicPlayerUiState.Playing,
    onIntent: (MusicPlayerUiIntent) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (maxWidth > maxHeight) {
            MusicPlayerLandscapeContent(state = state, onIntent = onIntent)
        } else {
            MusicPlayerPortraitContent(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun MusicPlayerPortraitContent(
    state: MusicPlayerUiState.Playing,
    onIntent: (MusicPlayerUiIntent) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MusicTopBar(
            title = state.title,
            onBack = { onIntent(MusicPlayerUiIntent.GoBack) },
            onOpenExternal = { onIntent(MusicPlayerUiIntent.OpenWithOtherPlayer) }
        )

        SpectrumModeSelector(
            selectedMode = state.spectrumMode,
            onModeClick = { onIntent(MusicPlayerUiIntent.SetSpectrumMode(it)) },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )

        MusicSpectrumArea(
            state = state,
            onIntent = onIntent,
            showOverview = true,
            compactOverview = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )

        MusicBottomControls(
            state = state,
            onIntent = onIntent,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MusicPlayerLandscapeContent(
    state: MusicPlayerUiState.Playing,
    onIntent: (MusicPlayerUiIntent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MusicSpectrumArea(
            state = state,
            onIntent = onIntent,
            showOverview = state.spectrumMode == MusicSpectrumMode.Spectrogram,
            compactOverview = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
        ) {
            MusicTopBar(
                title = state.title,
                onBack = { onIntent(MusicPlayerUiIntent.GoBack) },
                onOpenExternal = { onIntent(MusicPlayerUiIntent.OpenWithOtherPlayer) }
            )

            SpectrumModeSelector(
                selectedMode = state.spectrumMode,
                onModeClick = { onIntent(MusicPlayerUiIntent.SetSpectrumMode(it)) },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            MusicBottomControls(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MusicSpectrumArea(
    state: MusicPlayerUiState.Playing,
    onIntent: (MusicPlayerUiIntent) -> Unit,
    showOverview: Boolean,
    compactOverview: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SpectrumStage(
            state = state,
            onSeek = { position ->
                onIntent(MusicPlayerUiIntent.SeekBarDragEnd(position))
            },
            onDragStart = { onIntent(MusicPlayerUiIntent.SeekBarDragStart) },
            onDrag = { position -> onIntent(MusicPlayerUiIntent.SeekTo(position)) },
            onDragEnd = { position -> onIntent(MusicPlayerUiIntent.SeekBarDragEnd(position)) },
            onWindowChange = { startMs ->
                onIntent(MusicPlayerUiIntent.SetSpectrogramWindow(startMs))
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        if (showOverview) {
            MiniSpectrumOverview(
                overviewBitmap = state.spectrumOverviewBitmap,
                positionMs = state.currentPosition,
                durationMs = state.duration,
                windowStartMs = state.spectrogramWindowStartMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactOverview) 34.dp else 42.dp)
                    .padding(top = if (compactOverview) 6.dp else 0.dp)
            )
        }
    }
}

@Composable
private fun MusicTopBar(
    title: String,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_2),
                contentDescription = stringResource(R.string.iv_back_content_description),
                tint = Color.White
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_music),
            contentDescription = null,
            tint = MusicPink,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
            .padding(horizontal = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.music_player_title),
                color = MusicCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onOpenExternal) {
            Icon(
                painter = painterResource(R.drawable.ic_open_external),
                contentDescription = stringResource(R.string.text_open_external_player),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun SpectrumModeSelector(
    selectedMode: MusicSpectrumMode,
    onModeClick: (MusicSpectrumMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MusicSpectrumMode.values().forEach { mode ->
            val selected = mode == selectedMode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MusicPink.copy(alpha = 0.22f) else MusicPanel)
                    .border(
                        width = 1.dp,
                        color = if (selected) MusicPink else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .pointerInput(mode) {
                        detectTapGestures { onModeClick(mode) }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(mode.iconRes()),
                    contentDescription = null,
                    tint = if (selected) MusicPink else MusicMuted,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(mode.labelRes),
                    color = if (selected) Color.White else MusicMuted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SpectrumStage(
    state: MusicPlayerUiState.Playing,
    onSeek: (Long) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Long) -> Unit,
    onDragEnd: (Long) -> Unit,
    onWindowChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggingPosition by remember { mutableStateOf(0L) }
    var draggingWindowStart by remember { mutableStateOf(0L) }
    var spectrogramDragMode by remember { mutableStateOf<SpectrogramDragMode?>(null) }
    val gestureState by rememberUpdatedState(state)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MusicPanel)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val latestState = gestureState
                    if (latestState.spectrumMode != MusicSpectrumMode.Spectrogram) {
                        onSeek(seekPosition(offset.x, size.width.toFloat(), latestState.duration))
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val latestState = gestureState
                        if (latestState.spectrumMode == MusicSpectrumMode.Spectrogram) {
                            val chartLeft = SPECTROGRAM_AXIS_WIDTH_DP.dp.toPx()
                            val chartWidth = spectrogramChartWidth(
                                totalWidth = size.width.toFloat(),
                                chartLeft = chartLeft,
                                rightPadding = SPECTROGRAM_RIGHT_PADDING_DP.dp.toPx()
                            )
                            val playheadX = spectrogramPlayheadX(
                                chartLeft = chartLeft,
                                chartWidth = chartWidth,
                                positionMs = latestState.currentPosition,
                                windowStartMs = latestState.spectrogramWindowStartMs,
                                durationMs = latestState.duration
                            )
                            spectrogramDragMode = if (
                                playheadX != null &&
                                abs(offset.x - playheadX) <= SPECTROGRAM_PLAYHEAD_TOUCH_RADIUS_DP.dp.toPx()
                            ) {
                                onDragStart()
                                draggingPosition = spectrogramPositionFromOffset(
                                    x = offset.x,
                                    chartLeft = chartLeft,
                                    chartWidth = chartWidth,
                                    windowStartMs = latestState.spectrogramWindowStartMs,
                                    durationMs = latestState.duration
                                )
                                onDrag(draggingPosition)
                                SpectrogramDragMode.Playhead
                            } else {
                                draggingWindowStart = latestState.spectrogramWindowStartMs
                                SpectrogramDragMode.Window
                            }
                        } else {
                            spectrogramDragMode = null
                            onDragStart()
                            draggingPosition = seekPosition(
                                offset.x,
                                size.width.toFloat(),
                                latestState.duration
                            )
                            onDrag(draggingPosition)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val latestState = gestureState
                        when (spectrogramDragMode) {
                            SpectrogramDragMode.Playhead -> {
                                val chartLeft = SPECTROGRAM_AXIS_WIDTH_DP.dp.toPx()
                                val chartWidth = spectrogramChartWidth(
                                    totalWidth = size.width.toFloat(),
                                    chartLeft = chartLeft,
                                    rightPadding = SPECTROGRAM_RIGHT_PADDING_DP.dp.toPx()
                                )
                                draggingPosition = spectrogramPositionFromOffset(
                                    x = change.position.x,
                                    chartLeft = chartLeft,
                                    chartWidth = chartWidth,
                                    windowStartMs = latestState.spectrogramWindowStartMs,
                                    durationMs = latestState.duration
                                )
                                onDrag(draggingPosition)
                            }

                            SpectrogramDragMode.Window -> {
                                val chartWidth = spectrogramChartWidth(
                                    totalWidth = size.width.toFloat(),
                                    chartLeft = SPECTROGRAM_AXIS_WIDTH_DP.dp.toPx(),
                                    rightPadding = SPECTROGRAM_RIGHT_PADDING_DP.dp.toPx()
                                )
                                val windowDuration = spectrogramWindowDuration(latestState.duration)
                                val deltaMs = if (chartWidth > 0f) {
                                    (-dragAmount.x / chartWidth * windowDuration).toLong()
                                } else {
                                    0L
                                }
                                draggingWindowStart = clampSpectrogramWindowStart(
                                    startMs = draggingWindowStart + deltaMs,
                                    durationMs = latestState.duration
                                )
                                onWindowChange(draggingWindowStart)
                            }

                            null -> {
                                draggingPosition = seekPosition(
                                    change.position.x,
                                    size.width.toFloat(),
                                    latestState.duration
                                )
                                onDrag(draggingPosition)
                            }
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        val latestState = gestureState
                        if (spectrogramDragMode == SpectrogramDragMode.Playhead ||
                            latestState.spectrumMode != MusicSpectrumMode.Spectrogram
                        ) {
                            onDragEnd(draggingPosition)
                        }
                        spectrogramDragMode = null
                    },
                    onDragCancel = {
                        val latestState = gestureState
                        if (spectrogramDragMode == SpectrogramDragMode.Playhead ||
                            latestState.spectrumMode != MusicSpectrumMode.Spectrogram
                        ) {
                            onDragEnd(draggingPosition)
                        }
                        spectrogramDragMode = null
                    }
                )
            }
    ) {
        when (state.spectrumMode) {
            MusicSpectrumMode.Spectrogram -> SpectrogramCanvas(
                data = state.spectrumData,
                tiles = state.spectrogramTiles,
                positionMs = state.currentPosition,
                durationMs = state.duration,
                windowStartMs = state.spectrogramWindowStartMs,
                lowLabel = stringResource(R.string.music_spectrum_frequency_low),
                midLabel = stringResource(R.string.music_spectrum_frequency_mid),
                highLabel = stringResource(R.string.music_spectrum_frequency_high),
                modifier = Modifier.fillMaxSize()
            )

            MusicSpectrumMode.Realtime -> RealtimeSpectrumCanvas(
                frame = state.realtimeSpectrumFrame,
                positionMs = state.currentPosition,
                durationMs = state.duration,
                label = stringResource(R.string.music_spectrum_label_realtime),
                modifier = Modifier.fillMaxSize()
            )

            MusicSpectrumMode.Rhythm -> RhythmSpectrumCanvas(
                frame = state.realtimeSpectrumFrame,
                positionMs = state.currentPosition,
                durationMs = state.duration,
                label = stringResource(R.string.music_spectrum_label_rhythm),
                modifier = Modifier.fillMaxSize()
            )

            MusicSpectrumMode.Waveform -> WaveformCanvas(
                frame = state.realtimeSpectrumFrame,
                positionMs = state.currentPosition,
                durationMs = state.duration,
                label = stringResource(R.string.music_spectrum_label_waveform),
                modifier = Modifier.fillMaxSize()
            )
        }

        if (
            state.spectrumMode == MusicSpectrumMode.Spectrogram &&
            state.isAnalyzingSpectrum &&
            (state.spectrumData == null || state.spectrogramTiles.isEmpty())
        ) {
            AnalysisOverlay(progress = state.spectrumProgress)
        } else if (
            state.spectrumMode == MusicSpectrumMode.Spectrogram &&
            state.spectrumData == null &&
            state.spectrumError != null
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.spectrumError,
                    color = MusicMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AnalysisOverlay(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicBackground.copy(alpha = 0.54f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MusicPink, modifier = Modifier.size(34.dp))
            Text(
                text = stringResource(
                    R.string.music_spectrum_analyzing,
                    (progress.coerceIn(0f, 1f) * 100).toInt()
                ),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SpectrogramCanvas(
    data: MusicSpectrumData?,
    tiles: List<MusicSpectrumBitmapTile>,
    positionMs: Long,
    durationMs: Long,
    windowStartMs: Long,
    lowLabel: String,
    midLabel: String,
    highLabel: String,
    modifier: Modifier = Modifier
) {
    val imageTiles = remember(tiles) {
        tiles.map { SpectrogramImageTile(it, it.bitmap.asImageBitmap()) }
    }
    Canvas(modifier = modifier) {
        drawSpectrogram(
            data = data,
            tiles = imageTiles,
            positionMs = positionMs,
            durationMs = durationMs,
            windowStartMs = windowStartMs,
            lowLabel = lowLabel,
            midLabel = midLabel,
            highLabel = highLabel,
            drawAxis = true
        )
    }
}

@Composable
private fun RealtimeSpectrumCanvas(
    frame: RealtimeSpectrumFrame?,
    positionMs: Long,
    durationMs: Long,
    label: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRect(MusicPanel)
        val barCount = frame?.spectrum?.size?.coerceAtLeast(1) ?: REALTIME_BAR_COUNT
        val horizontalPadding = 22.dp.toPx()
        val bottomPadding = 32.dp.toPx()
        val topPadding = 28.dp.toPx()
        val minHeight = 6.dp.toPx()
        val chartWidth = (size.width - horizontalPadding * 2).coerceAtLeast(0f)
        val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(minHeight)
        val barGap = 2.dp.toPx()
        val barWidth = ((chartWidth - barGap * (barCount - 1)) / barCount)
            .coerceAtLeast(1.dp.toPx())

        for (index in 0 until barCount) {
            val sourceIndex = frame?.spectrumIndexFor(index, barCount) ?: 0
            val base = frame?.smoothedSpectrumValue(sourceIndex) ?: 0f
            val value = base.coerceIn(0f, 1f)
            val left = horizontalPadding + index * (barWidth + barGap)
            val height = (chartHeight * value).coerceIn(minHeight, chartHeight)
            val top = topPadding + chartHeight - height
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(MusicCyan, MusicPink, MusicAmber),
                    startY = top,
                    endY = top + height
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }

        drawCenteredLabel(label, size.width / 2f, topPadding - 8.dp.toPx())
        drawTimeText(positionMs, durationMs)
    }
}

@Composable
private fun RhythmSpectrumCanvas(
    frame: RealtimeSpectrumFrame?,
    positionMs: Long,
    durationMs: Long,
    label: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRect(MusicPanel)
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = minOf(size.width, size.height) * 0.22f
        val maxRadius = minOf(size.width, size.height) * 0.38f
        val points = frame?.spectrum?.size?.coerceAtLeast(24) ?: REALTIME_BAR_COUNT
        val path = Path()

        for (index in 0..points) {
            val ratio = index.toFloat() / points
            val angle = ratio * 2f * PI.toFloat()
            val sourceIndex = frame?.spectrumIndexFor(index, points) ?: 0
            val energy = frame?.smoothedSpectrumValue(sourceIndex) ?: 0f
            val radius = baseRadius + (maxRadius - baseRadius) * energy
            val x = center.x + cos(angle) * radius
            val y = center.y + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawCircle(
            color = MusicCyan.copy(alpha = 0.10f),
            radius = maxRadius * 1.15f,
            center = center
        )
        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(MusicPink.copy(alpha = 0.38f), MusicCyan.copy(alpha = 0.08f)),
                center = center,
                radius = maxRadius
            )
        )
        drawPath(
            path = path,
            color = MusicCyan,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color = MusicPink, radius = 5.dp.toPx(), center = center)
        drawCenteredLabel(label, size.width / 2f, 28.dp.toPx())
        drawTimeText(positionMs, durationMs)
    }
}

@Composable
private fun WaveformCanvas(
    frame: RealtimeSpectrumFrame?,
    positionMs: Long,
    durationMs: Long,
    label: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRect(MusicPanel)
        val horizontalPadding = 20.dp.toPx()
        val centerY = size.height / 2f
        val chartWidth = size.width - horizontalPadding * 2
        val sampleCount = 160
        val step = chartWidth / sampleCount

        for (index in 0 until sampleCount) {
            val sourceIndex = frame?.waveformIndexFor(index, sampleCount) ?: 0
            val value = frame?.waveform?.getOrNull(sourceIndex) ?: 0f
            val x = horizontalPadding + index * step
            val height = max(2.dp.toPx(), value * size.height * 0.38f)
            val color = if (index <= sampleCount / 2) MusicPink else MusicMuted.copy(alpha = 0.36f)
            drawLine(
                color = color,
                start = Offset(x, centerY - height),
                end = Offset(x, centerY + height),
                strokeWidth = max(1f, step * 0.62f),
                cap = StrokeCap.Round
            )
        }

        val playX = horizontalPadding + chartWidth * 0.5f
        drawPlayHead(playX, 18.dp.toPx(), size.height - 22.dp.toPx())
        drawCenteredLabel(label, size.width / 2f, 28.dp.toPx())
        drawTimeText(positionMs, durationMs)
    }
}

@Composable
private fun MiniSpectrumOverview(
    overviewBitmap: android.graphics.Bitmap?,
    positionMs: Long,
    durationMs: Long,
    windowStartMs: Long,
    modifier: Modifier = Modifier
) {
    val overviewImage = remember(overviewBitmap) {
        overviewBitmap?.asImageBitmap()
    }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MusicPanel)
    ) {
        if (overviewImage != null) {
            drawImage(
                image = overviewImage,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(overviewImage.width, overviewImage.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
        } else {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(MusicPanel, Color(0xFF10192D), MusicPanel)
                )
            )
        }
        val x = size.width * playbackRatio(positionMs, durationMs)
        val windowDuration = spectrogramWindowDuration(durationMs)
        val windowLeft = size.width * playbackRatio(windowStartMs, durationMs)
        val windowWidth = size.width * playbackRatio(windowDuration, durationMs)
        drawRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = Offset(windowLeft, 0f),
            size = Size(windowWidth.coerceAtLeast(2.dp.toPx()), size.height)
        )
        drawRoundRect(
            color = MusicCyan.copy(alpha = 0.7f),
            topLeft = Offset(windowLeft, 1.dp.toPx()),
            size = Size(windowWidth.coerceAtLeast(2.dp.toPx()), size.height - 2.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        drawLine(
            color = MusicCyan,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun MusicBottomControls(
    state: MusicPlayerUiState.Playing,
    onIntent: (MusicPlayerUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = state.duration.coerceAtLeast(1L)
    var sliderPosition by remember(state.currentPosition, state.isSeekBarDragging) {
        mutableFloatStateOf(state.currentPosition.toFloat())
    }

    Column(
        modifier = modifier
            .background(MusicBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Slider(
            value = sliderPosition.coerceIn(0f, duration.toFloat()),
            onValueChange = { value ->
                sliderPosition = value
                onIntent(MusicPlayerUiIntent.SeekBarDragStart)
                onIntent(MusicPlayerUiIntent.SeekTo(value.toLong()))
            },
            onValueChangeFinished = {
                onIntent(MusicPlayerUiIntent.SeekBarDragEnd(sliderPosition.toLong()))
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MusicPink,
                activeTrackColor = MusicPink,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(state.currentPosition),
                color = MusicMuted,
                fontSize = 12.sp,
                modifier = Modifier.width(48.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { onIntent(MusicPlayerUiIntent.TogglePlayPause) },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MusicPink)
            ) {
                Icon(
                    painter = painterResource(
                        if (state.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = formatTime(state.duration),
                color = MusicMuted,
                fontSize = 12.sp,
                modifier = Modifier.width(48.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedControl(state = state, onIntent = onIntent)
            Spacer(modifier = Modifier.width(18.dp))
            IconButton(
                onClick = { onIntent(MusicPlayerUiIntent.OpenWithOtherPlayer) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_open_external),
                    contentDescription = stringResource(R.string.text_open_external_player),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeedControl(
    state: MusicPlayerUiState.Playing,
    onIntent: (MusicPlayerUiIntent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val speedOptions = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.size(width = 64.dp, height = 40.dp)
        ) {
            Text(
                text = formatSpeed(state.selectedPlaybackSpeed),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MusicPanel)
        ) {
            speedOptions.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatSpeed(speed),
                            color = if (speed == state.selectedPlaybackSpeed) MusicPink else Color.White
                        )
                    },
                    onClick = {
                        expanded = false
                        onIntent(MusicPlayerUiIntent.SetPlaybackSpeed(speed))
                    }
                )
            }
        }
    }
}

private fun DrawScope.drawSpectrogram(
    data: MusicSpectrumData?,
    tiles: List<SpectrogramImageTile>,
    positionMs: Long,
    durationMs: Long,
    windowStartMs: Long,
    lowLabel: String,
    midLabel: String,
    highLabel: String,
    drawAxis: Boolean
) {
    drawRect(MusicPanel)
    val axisWidth = if (drawAxis) SPECTROGRAM_AXIS_WIDTH_DP.dp.toPx() else 0f
    val bottomAxis = if (drawAxis) 24.dp.toPx() else 0f
    val topPadding = 10.dp.toPx()
    val rightPadding = SPECTROGRAM_RIGHT_PADDING_DP.dp.toPx()
    val chartLeft = axisWidth
    val chartTop = topPadding
    val chartWidth = size.width - axisWidth - rightPadding
    val chartHeight = size.height - topPadding - bottomAxis
    val windowDuration = spectrogramWindowDuration(durationMs)
    val windowStart = clampSpectrogramWindowStart(windowStartMs, durationMs)
    val windowEnd = windowStart + windowDuration

    if (tiles.isNotEmpty()) {
        tiles.forEach { imageTile ->
            drawSpectrogramTile(
                imageTile = imageTile,
                windowStartMs = windowStart,
                windowEndMs = windowEnd,
                windowDurationMs = windowDuration,
                chartLeft = chartLeft,
                chartTop = chartTop,
                chartWidth = chartWidth,
                chartHeight = chartHeight
            )
        }
    } else {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(MusicPanel, Color(0xFF10192D), MusicPanel)
            ),
            topLeft = Offset(chartLeft, chartTop),
            size = Size(chartWidth, chartHeight)
        )
    }

    drawGridAndAxis(
        chartLeft = chartLeft,
        chartTop = chartTop,
        chartWidth = chartWidth,
        chartHeight = chartHeight,
        windowStartMs = windowStart,
        windowDurationMs = windowDuration,
        sampleRate = data?.sampleRate ?: 44_100,
        lowLabel = lowLabel,
        midLabel = midLabel,
        highLabel = highLabel
    )

    if (positionMs in windowStart..windowEnd) {
        val playX = chartLeft + chartWidth * (
            (positionMs - windowStart).toFloat() / windowDuration.coerceAtLeast(1L)
        ).coerceIn(0f, 1f)
        drawPlayHead(playX, chartTop, chartTop + chartHeight)
    }
}

private fun DrawScope.drawSpectrogramTile(
    imageTile: SpectrogramImageTile,
    windowStartMs: Long,
    windowEndMs: Long,
    windowDurationMs: Long,
    chartLeft: Float,
    chartTop: Float,
    chartWidth: Float,
    chartHeight: Float
) {
    val tile = imageTile.tile
    val tileStart = tile.startMs
    val tileEnd = tile.startMs + tile.durationMs
    val overlapStart = maxOf(windowStartMs, tileStart)
    val overlapEnd = minOf(windowEndMs, tileEnd)
    if (overlapEnd <= overlapStart) return

    val tileDuration = tile.durationMs.coerceAtLeast(1L)
    val srcStartX = (((overlapStart - tileStart).toFloat() / tileDuration) * imageTile.image.width)
        .roundToInt()
        .coerceIn(0, imageTile.image.width - 1)
    val srcEndX = (((overlapEnd - tileStart).toFloat() / tileDuration) * imageTile.image.width)
        .roundToInt()
        .coerceIn(srcStartX + 1, imageTile.image.width)
    val dstStartX = chartLeft + (
        (overlapStart - windowStartMs).toFloat() / windowDurationMs.coerceAtLeast(1L)
    ) * chartWidth
    val dstEndX = chartLeft + (
        (overlapEnd - windowStartMs).toFloat() / windowDurationMs.coerceAtLeast(1L)
    ) * chartWidth

    drawImage(
        image = imageTile.image,
        srcOffset = IntOffset(srcStartX, 0),
        srcSize = IntSize(srcEndX - srcStartX, imageTile.image.height),
        dstOffset = IntOffset(dstStartX.roundToInt(), chartTop.roundToInt()),
        dstSize = IntSize(
            (dstEndX - dstStartX).roundToInt().coerceAtLeast(1),
            chartHeight.roundToInt().coerceAtLeast(1)
        )
    )
}

private fun DrawScope.drawGridAndAxis(
    chartLeft: Float,
    chartTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    windowStartMs: Long,
    windowDurationMs: Long,
    sampleRate: Int,
    lowLabel: String,
    midLabel: String,
    highLabel: String
) {
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MusicMuted.toArgb()
        textSize = 10.sp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    repeat(5) { index ->
        val ratio = index / 4f
        val x = chartLeft + chartWidth * ratio
        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(x, chartTop),
            end = Offset(x, chartTop + chartHeight),
            strokeWidth = 1f
        )
        drawContext.canvas.nativeCanvas.drawText(
            formatTime(windowStartMs + (windowDurationMs * ratio).toLong()),
            x - 14.dp.toPx(),
            chartTop + chartHeight + 17.dp.toPx(),
            labelPaint
        )
    }

    val frequencyLabels = listOf(
        0.1f to lowLabel,
        0.42f to midLabel,
        0.72f to highLabel,
        0.94f to formatFrequency(sampleRate / 2)
    )
    frequencyLabels.forEach { (ratio, label) ->
        val y = chartTop + chartHeight * (1f - ratio)
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(chartLeft, y),
            end = Offset(chartLeft + chartWidth, y),
            strokeWidth = 1f
        )
        drawContext.canvas.nativeCanvas.drawText(label, 8.dp.toPx(), y + 4.dp.toPx(), labelPaint)
    }
}

private fun DrawScope.drawPlayHead(x: Float, top: Float, bottom: Float) {
    drawLine(
        color = MusicCyan,
        start = Offset(x, top),
        end = Offset(x, bottom),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(x, top + 7.dp.toPx()))
    drawCircle(color = MusicCyan, radius = 2.5.dp.toPx(), center = Offset(x, top + 7.dp.toPx()))
}

private fun DrawScope.drawTimeText(positionMs: Long, durationMs: Long) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MusicMuted.toArgb()
        textSize = 12.sp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    drawContext.canvas.nativeCanvas.drawText(
        "${formatTime(positionMs)} / ${formatTime(durationMs)}",
        size.width - 16.dp.toPx(),
        size.height - 18.dp.toPx(),
        textPaint
    )
}

private fun DrawScope.drawCenteredLabel(text: String, x: Float, y: Float) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.copy(alpha = 0.82f).toArgb()
        textSize = 13.sp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, textPaint)
}

private fun MusicSpectrumMode.iconRes(): Int = when (this) {
    MusicSpectrumMode.Spectrogram -> R.drawable.ic_spectrum
    MusicSpectrumMode.Realtime -> R.drawable.ic_equalizer
    MusicSpectrumMode.Rhythm -> R.drawable.ic_rhythm
    MusicSpectrumMode.Waveform -> R.drawable.ic_waveform
}

private fun heatColor(value: Float): Color {
    val safe = value.coerceIn(0f, 1f)
    val stops = listOf(
        0.00f to Color(0xFF07111F),
        0.22f to Color(0xFF184C8A),
        0.48f to Color(0xFF7A3FC3),
        0.70f to MusicPink,
        0.88f to MusicAmber,
        1.00f to Color(0xFFFFF4A4)
    )
    for (index in 0 until stops.lastIndex) {
        val start = stops[index]
        val end = stops[index + 1]
        if (safe in start.first..end.first) {
            val ratio = (safe - start.first) / (end.first - start.first)
            return lerp(start.second, end.second, ratio)
        }
    }
    return stops.last().second
}

private fun playbackRatio(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

private fun seekPosition(x: Float, width: Float, durationMs: Long): Long {
    if (durationMs <= 0L || width <= 0f) return 0L
    return (durationMs * (x / width).coerceIn(0f, 1f)).toLong()
}

private fun MusicSpectrumData.smoothedSpectrumValue(column: Int, row: Int): Float {
    if (columns == 0 || rows == 0) return 0f
    var sum = 0f
    var count = 0
    for (columnOffset in -1..1) {
        for (rowOffset in -1..1) {
            sum += spectrumValue(column + columnOffset, row + rowOffset)
            count++
        }
    }
    return (sum / count).coerceIn(0f, 1f)
}

private fun RealtimeSpectrumFrame.spectrumIndexFor(index: Int, targetCount: Int): Int {
    if (spectrum.isEmpty() || targetCount <= 1) return 0
    return (index.toFloat() / (targetCount - 1).toFloat() * (spectrum.size - 1))
        .roundToInt()
        .coerceIn(0, spectrum.lastIndex)
}

private fun RealtimeSpectrumFrame.waveformIndexFor(index: Int, targetCount: Int): Int {
    if (waveform.isEmpty() || targetCount <= 1) return 0
    return (index.toFloat() / (targetCount - 1).toFloat() * (waveform.size - 1))
        .roundToInt()
        .coerceIn(0, waveform.lastIndex)
}

private fun RealtimeSpectrumFrame.smoothedSpectrumValue(index: Int): Float {
    if (spectrum.isEmpty()) return 0f
    var sum = 0f
    var count = 0
    for (offset in -1..1) {
        sum += spectrum[(index + offset).coerceIn(0, spectrum.lastIndex)]
        count++
    }
    return (sum / count).coerceIn(0f, 1f)
}

private fun spectrogramPositionFromOffset(
    x: Float,
    chartLeft: Float,
    chartWidth: Float,
    windowStartMs: Long,
    durationMs: Long
): Long {
    if (durationMs <= 0L || chartWidth <= 0f) return 0L
    val windowDuration = spectrogramWindowDuration(durationMs)
    val ratio = ((x - chartLeft) / chartWidth).coerceIn(0f, 1f)
    return (windowStartMs + windowDuration * ratio).toLong().coerceIn(0L, durationMs)
}

private fun spectrogramPlayheadX(
    chartLeft: Float,
    chartWidth: Float,
    positionMs: Long,
    windowStartMs: Long,
    durationMs: Long
): Float? {
    if (durationMs <= 0L || chartWidth <= 0f) return null
    val windowDuration = spectrogramWindowDuration(durationMs)
    val windowEndMs = windowStartMs + windowDuration
    if (positionMs !in windowStartMs..windowEndMs) return null
    val ratio = ((positionMs - windowStartMs).toFloat() / windowDuration).coerceIn(0f, 1f)
    return chartLeft + chartWidth * ratio
}

private fun spectrogramChartWidth(
    totalWidth: Float,
    chartLeft: Float,
    rightPadding: Float
): Float {
    return (totalWidth - chartLeft - rightPadding).coerceAtLeast(0f)
}

private fun spectrogramWindowDuration(durationMs: Long): Long {
    if (durationMs <= 0L) return SPECTROGRAM_WINDOW_DURATION_MS
    return durationMs.coerceAtMost(SPECTROGRAM_WINDOW_DURATION_MS)
}

private fun clampSpectrogramWindowStart(startMs: Long, durationMs: Long): Long {
    val maxStart = (durationMs - spectrogramWindowDuration(durationMs)).coerceAtLeast(0L)
    return startMs.coerceIn(0L, maxStart)
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1.0f == 0f) {
        "${speed.toInt()}x"
    } else {
        "${String.format("%.2f", speed).trimEnd('0').trimEnd('.')}x"
    }
}

private fun formatTime(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    val totalSeconds = safeMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatFrequency(frequency: Int): String {
    return if (frequency >= 1000) "${frequency / 1000}k" else "${frequency}Hz"
}

private enum class SpectrogramDragMode {
    Playhead,
    Window
}

private data class SpectrogramImageTile(
    val tile: MusicSpectrumBitmapTile,
    val image: ImageBitmap
)

private const val SPECTROGRAM_WINDOW_DURATION_MS = 60_000L
private const val SPECTROGRAM_AXIS_WIDTH_DP = 42
private const val SPECTROGRAM_RIGHT_PADDING_DP = 8
private const val SPECTROGRAM_PLAYHEAD_TOUCH_RADIUS_DP = 28
private const val REALTIME_BAR_COUNT = 96
private const val REALTIME_WAVEFORM_WINDOW_MS = 12_000L

private val MusicBackground = Color(0xFF080B12)
private val MusicPanel = Color(0xFF10131D)
private val MusicMuted = Color(0xFFA7B0C2)
private val MusicPink = Color(0xFFFA7298)
private val MusicCyan = Color(0xFF51DDE7)
private val MusicAmber = Color(0xFFFFB84D)
