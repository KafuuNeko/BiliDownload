package cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer

import android.content.Context

sealed class MediaPlayerUiIntent {
    data class Init(val context: Context, val filePath: String, val title: String) : MediaPlayerUiIntent()
    data object TogglePlayPause : MediaPlayerUiIntent()
    data class SeekTo(val position: Long) : MediaPlayerUiIntent()
    data object SeekBarDragStart : MediaPlayerUiIntent()
    data class SeekBarDragEnd(val position: Long) : MediaPlayerUiIntent()
    data object LongPressStart : MediaPlayerUiIntent()
    data object LongPressEnd : MediaPlayerUiIntent()
    data object ToggleControls : MediaPlayerUiIntent()
    data object ToggleFullScreen : MediaPlayerUiIntent()
    data object GoBack : MediaPlayerUiIntent()
    data class UpdateProgress(val position: Long, val duration: Long) : MediaPlayerUiIntent()
}
