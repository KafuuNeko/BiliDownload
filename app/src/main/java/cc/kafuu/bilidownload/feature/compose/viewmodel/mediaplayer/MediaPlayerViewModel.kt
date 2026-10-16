package cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer

import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import cc.kafuu.bilidownload.common.audio.MediaPlayerFactory
import cc.kafuu.bilidownload.common.core.compose.CoreCompViewModelWithEvent
import cc.kafuu.bilidownload.common.core.compose.UiIntentObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 在视频页面的 ViewModel 生命周期内持有播放器及进度任务，页面重建时复用播放会话。
 *
 * 播放器操作由主线程处理，仅在 [onCleared] 中释放；Activity 负责窗口和画中画状态。
 */
class MediaPlayerViewModel :
    CoreCompViewModelWithEvent<MediaPlayerUiIntent, MediaPlayerUiState, MediaPlayerUiEvent>(
        MediaPlayerUiState.None
    ) {

    private var mPlayer: ExoPlayer? = null
    private var mProgressJob: Job? = null
    private var mAutoHideJob: Job? = null
    private var mSelectedPlaybackSpeed = 1.0f

    private fun createPlayerListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val player = mPlayer ?: return
                    getOrNull<MediaPlayerUiState.Playing>()?.copy(
                        duration = player.duration,
                        isPlaying = player.isPlaying
                    )?.setup()
                }

                Player.STATE_ENDED -> {
                    getOrNull<MediaPlayerUiState.Playing>()?.copy(
                        isPlaying = false,
                        currentPosition = getOrNull<MediaPlayerUiState.Playing>()?.duration ?: 0L
                    )?.setup()
                    stopProgressUpdate()
                }

                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            getOrNull<MediaPlayerUiState.Playing>()?.copy(isPlaying = isPlaying)?.setup()
            if (isPlaying) startProgressUpdate() else stopProgressUpdate()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            getOrNull<MediaPlayerUiState.Playing>()?.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height
            )?.setup()
        }

        override fun onPlayerError(error: PlaybackException) {
            MediaPlayerUiState.Error(
                error.localizedMessage ?: "Playback error"
            ).setup()
        }
    }

    /**
     * 绑定已有媒体会话，或为新的播放请求准备媒体并从头播放。
     *
     * 同一 URI 的页面重建保留进度、倍速、暂停及错误状态，不重复准备播放器。
     * 显式新请求仍可重新打开同一 URI，以支持重新播放或重试失败的资源。
     *
     * @param intent 播放参数，contentUri 优先于文件路径；仅使用其中的 applicationContext。
     */
    @UiIntentObserver(MediaPlayerUiIntent.Init::class)
    fun onInit(intent: MediaPlayerUiIntent.Init) {
        // 重建只重新绑定视图，不将仍由 ViewModel 持有的会话当作一次新的播放请求。
        val uri = intent.contentUri?.let(Uri::parse) ?: Uri.fromFile(File(intent.filePath))
        if (!intent.restartPlayback && mPlayer?.currentMediaItem?.localConfiguration?.uri == uri) return

        // 切换或重新打开媒体时重置会话状态，播放器实例及监听器仍由当前 ViewModel 复用。
        stopProgressUpdate()
        cancelAutoHide()
        mSelectedPlaybackSpeed = 1.0f

        val player = mPlayer ?: MediaPlayerFactory.configure(
            ExoPlayer.Builder(intent.context.applicationContext)
        ).build().also {
            mPlayer = it
            it.addListener(createPlayerListener())
        }
        player.setPlaybackSpeed(mSelectedPlaybackSpeed)

        MediaPlayerUiState.Playing(
            title = intent.title,
            filePath = intent.filePath,
            contentUri = intent.contentUri,
            mimeType = intent.mimeType,
            player = player,
        ).setup()

        // 先发布新媒体的展示状态，让准备资源时的播放器回调能够更新对应界面。
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        scheduleAutoHide()
    }

    @UiIntentObserver(MediaPlayerUiIntent.SetPlaybackSpeed::class)
    fun onSetPlaybackSpeed(intent: MediaPlayerUiIntent.SetPlaybackSpeed) {
        val speed = intent.speed
        mSelectedPlaybackSpeed = speed
        mPlayer?.setPlaybackSpeed(speed)
        getOrNull<MediaPlayerUiState.Playing>()?.copy(
            playbackSpeed = speed,
            selectedPlaybackSpeed = speed,
            isLongPressing = false
        )?.setup()
        scheduleAutoHide()
    }

    @UiIntentObserver(MediaPlayerUiIntent.TogglePlayPause::class)
    fun onTogglePlayPause() {
        val player = mPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
        scheduleAutoHide()
    }

    @UiIntentObserver(MediaPlayerUiIntent.Pause::class)
    fun onPause() {
        mPlayer?.pause()
    }

    @UiIntentObserver(MediaPlayerUiIntent.SeekTo::class)
    fun onSeekTo(intent: MediaPlayerUiIntent.SeekTo) {
        getOrNull<MediaPlayerUiState.Playing>()?.copy(
            currentPosition = intent.position
        )?.setup()
        scheduleAutoHide()
    }

    @UiIntentObserver(MediaPlayerUiIntent.SeekBarDragStart::class)
    fun onSeekBarDragStart() {
        cancelAutoHide()
        getOrNull<MediaPlayerUiState.Playing>()?.copy(isSeekBarDragging = true)?.setup()
    }

    @UiIntentObserver(MediaPlayerUiIntent.SeekBarDragEnd::class)
    fun onSeekBarDragEnd(intent: MediaPlayerUiIntent.SeekBarDragEnd) {
        mPlayer?.seekTo(intent.position)
        getOrNull<MediaPlayerUiState.Playing>()?.copy(
            isSeekBarDragging = false,
            currentPosition = intent.position
        )?.setup()
        scheduleAutoHide()
    }

    @UiIntentObserver(MediaPlayerUiIntent.LongPressStart::class)
    fun onLongPressStart() {
        val speed = 2.0f
        mPlayer?.setPlaybackSpeed(speed)
        if (mPlayer?.isPlaying == false) mPlayer?.play()
        getOrNull<MediaPlayerUiState.Playing>()?.copy(
            isLongPressing = true,
            playbackSpeed = speed
        )?.setup()
    }

    @UiIntentObserver(MediaPlayerUiIntent.LongPressEnd::class)
    fun onLongPressEnd() {
        val state = getOrNull<MediaPlayerUiState.Playing>() ?: return
        if (!state.isLongPressing) return
        val speed = mSelectedPlaybackSpeed
        mPlayer?.setPlaybackSpeed(speed)
        state.copy(
            isLongPressing = false,
            playbackSpeed = speed
        ).setup()
    }

    @UiIntentObserver(MediaPlayerUiIntent.ToggleControls::class)
    fun onToggleControls() {
        val state = getOrNull<MediaPlayerUiState.Playing>() ?: return
        val newShow = !state.showControls
        state.copy(showControls = newShow).setup()
        if (newShow) scheduleAutoHide() else cancelAutoHide()
    }

    @UiIntentObserver(MediaPlayerUiIntent.ToggleFullScreen::class)
    fun onToggleFullScreen() = viewModelScope.launch {
        val state = getOrNull<MediaPlayerUiState.Playing>() ?: return@launch
        val newFullScreen = !state.isFullScreen
        state.copy(isFullScreen = newFullScreen).setup()
        MediaPlayerUiEvent.SetFullScreen(newFullScreen).send()
        scheduleAutoHide()
    }

    @UiIntentObserver(MediaPlayerUiIntent.OpenWithOtherPlayer::class)
    fun onOpenWithOtherPlayer() = viewModelScope.launch {
        val state = getOrNull<MediaPlayerUiState.Playing>() ?: return@launch
        MediaPlayerUiEvent.OpenWithOtherPlayer(
            filePath = state.filePath,
            title = state.title,
            mimeType = state.mimeType,
            contentUri = state.contentUri
        ).send()
        scheduleAutoHide()
    }

    @UiIntentObserver(MediaPlayerUiIntent.GoBack::class)
    fun onGoBack() = viewModelScope.launch {
        val state = getOrNull<MediaPlayerUiState.Playing>()
        if (state?.isFullScreen == true) {
            state.copy(isFullScreen = false).setup()
            MediaPlayerUiEvent.SetFullScreen(false).send()
        } else {
            MediaPlayerUiEvent.RequestPictureInPicture.send()
        }
    }

    @UiIntentObserver(MediaPlayerUiIntent.UpdateProgress::class)
    fun onUpdateProgress(intent: MediaPlayerUiIntent.UpdateProgress) {
        val state = getOrNull<MediaPlayerUiState.Playing>() ?: return
        if (!state.isSeekBarDragging) {
            state.copy(
                currentPosition = intent.position,
                duration = intent.duration
            ).setup()
        }
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        mProgressJob = viewModelScope.launch {
            while (true) {
                val player = mPlayer ?: break
                emit(
                    MediaPlayerUiIntent.UpdateProgress(
                        position = player.currentPosition,
                        duration = player.duration.coerceAtLeast(0)
                    )
                )
                delay(300)
            }
        }
    }

    private fun stopProgressUpdate() {
        mProgressJob?.cancel()
        mProgressJob = null
    }

    private fun scheduleAutoHide() {
        cancelAutoHide()
        mAutoHideJob = viewModelScope.launch {
            delay(3000)
            getOrNull<MediaPlayerUiState.Playing>()?.copy(showControls = false)?.setup()
        }
    }

    private fun cancelAutoHide() {
        mAutoHideJob?.cancel()
        mAutoHideJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        cancelAutoHide()
        mPlayer?.release()
        mPlayer = null
    }
}
