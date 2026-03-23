package cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import cc.kafuu.bilidownload.common.core.compose.CoreCompViewModelWithEvent
import cc.kafuu.bilidownload.common.core.compose.UiIntentObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MediaPlayerViewModel :
    CoreCompViewModelWithEvent<MediaPlayerUiIntent, MediaPlayerUiState, MediaPlayerUiEvent>(
        MediaPlayerUiState.None
    ) {

    private var mPlayer: ExoPlayer? = null
    private var mProgressJob: Job? = null
    private var mAutoHideJob: Job? = null

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

        override fun onPlayerError(error: PlaybackException) {
            MediaPlayerUiState.Error(
                error.localizedMessage ?: "Playback error"
            ).setup()
        }
    }

    @UiIntentObserver(MediaPlayerUiIntent.Init::class)
    fun onInit(intent: MediaPlayerUiIntent.Init) {
        if (!isStateOf<MediaPlayerUiState.None>()) return

        val player = ExoPlayer.Builder(intent.context.applicationContext).build().also {
            mPlayer = it
            it.addListener(createPlayerListener())
        }

        MediaPlayerUiState.Playing(
            title = intent.title,
            filePath = intent.filePath,
            player = player,
        ).setup()

        val uri = Uri.fromFile(File(intent.filePath))
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

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
        val speed = 1.0f
        mPlayer?.setPlaybackSpeed(speed)
        getOrNull<MediaPlayerUiState.Playing>()?.copy(
            isLongPressing = false,
            playbackSpeed = speed
        )?.setup()
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

    @UiIntentObserver(MediaPlayerUiIntent.GoBack::class)
    fun onGoBack() = viewModelScope.launch {
        val state = getOrNull<MediaPlayerUiState.Playing>()
        if (state?.isFullScreen == true) {
            state.copy(isFullScreen = false).setup()
            MediaPlayerUiEvent.SetFullScreen(false).send()
        } else {
            MediaPlayerUiEvent.Finish.send()
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
