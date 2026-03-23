package cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer

import androidx.media3.exoplayer.ExoPlayer

sealed class MediaPlayerUiState {
    data object None : MediaPlayerUiState()

    data class Playing(
        val title: String,
        val filePath: String,
        val player: ExoPlayer,
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val playbackSpeed: Float = 1.0f,
        val isLongPressing: Boolean = false,
        val isSeekBarDragging: Boolean = false,
        val showControls: Boolean = true,
        val isFullScreen: Boolean = false,
    ) : MediaPlayerUiState()

    data class Error(val message: String) : MediaPlayerUiState()
}
