package cc.kafuu.bilidownload.feature.compose.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiState

@Composable
fun MediaPlayerLayout(
    state: MediaPlayerUiState,
    onIntent: (MediaPlayerUiIntent) -> Unit
) {
    when (state) {
        MediaPlayerUiState.None -> LoadingView()
        is MediaPlayerUiState.Playing -> PlayerContent(state, onIntent)
        is MediaPlayerUiState.Error -> ErrorView(state.message, onIntent)
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun ErrorView(message: String, onIntent: (MediaPlayerUiIntent) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
            IconButton(onClick = { onIntent(MediaPlayerUiIntent.GoBack) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_2),
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PlayerContent(
    state: MediaPlayerUiState.Playing,
    onIntent: (MediaPlayerUiIntent) -> Unit
) {
    val player = state.player

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 视频渲染区域
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        // 触摸检测层 - 点击切换控制栏，长按加速
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onIntent(MediaPlayerUiIntent.ToggleControls) },
                        onLongPress = { onIntent(MediaPlayerUiIntent.LongPressStart) },
                        onPress = {
                            tryAwaitRelease()
                            onIntent(MediaPlayerUiIntent.LongPressEnd)
                        }
                    )
                }
        )

        // 长按加速提示
        AnimatedVisibility(
            visible = state.isLongPressing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = stringResource(R.string.speed_indicator, state.playbackSpeed),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 控制栏
        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶栏
                TopBar(
                    title = state.title,
                    onBack = { onIntent(MediaPlayerUiIntent.GoBack) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                )

                // 底部控制栏
                BottomControls(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
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
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )
    }
}

@Composable
private fun BottomControls(
    state: MediaPlayerUiState.Playing,
    onIntent: (MediaPlayerUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = state.duration.coerceAtLeast(1L)
    var sliderPosition by remember(state.currentPosition, state.isSeekBarDragging) {
        mutableFloatStateOf(state.currentPosition.toFloat())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 进度条
        Slider(
            value = sliderPosition,
            onValueChange = { value ->
                sliderPosition = value
                onIntent(MediaPlayerUiIntent.SeekBarDragStart)
                onIntent(MediaPlayerUiIntent.SeekTo(value.toLong()))
            },
            onValueChangeFinished = {
                onIntent(MediaPlayerUiIntent.SeekBarDragEnd(sliderPosition.toLong()))
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 播放按钮、时间、全屏按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onIntent(MediaPlayerUiIntent.TogglePlayPause) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.isPlaying) R.drawable.ic_media_pause
                            else R.drawable.ic_media_play
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${formatTime(state.currentPosition)} / ${formatTime(state.duration)}",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }

            // 全屏切换按钮
            IconButton(
                onClick = { onIntent(MediaPlayerUiIntent.ToggleFullScreen) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (state.isFullScreen) R.drawable.ic_media_fullscreen_exit
                        else R.drawable.ic_media_fullscreen
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
