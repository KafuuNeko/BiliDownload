package cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer

import android.content.Context

sealed class MediaPlayerUiIntent {
    /**
     * 将宿主播放参数交给 ViewModel；相同媒体的重复绑定默认保留现有会话。
     *
     * @param restartPlayback 外部新播放请求设为 true，即使 URI 相同也重新准备并从头播放；
     * 页面重建保持 false，避免恢复视图时丢失用户的进度与播放设置。
     */
    data class Init(
        val context: Context,
        val filePath: String,
        val title: String,
        val mimeType: String,
        val contentUri: String?,
        val restartPlayback: Boolean = false,
    ) : MediaPlayerUiIntent()
    data object TogglePlayPause : MediaPlayerUiIntent()
    data object Pause : MediaPlayerUiIntent()
    data class SetPlaybackSpeed(val speed: Float) : MediaPlayerUiIntent()
    data class SeekTo(val position: Long) : MediaPlayerUiIntent()
    data object SeekBarDragStart : MediaPlayerUiIntent()
    data class SeekBarDragEnd(val position: Long) : MediaPlayerUiIntent()
    data object LongPressStart : MediaPlayerUiIntent()
    data object LongPressEnd : MediaPlayerUiIntent()
    data object ToggleControls : MediaPlayerUiIntent()
    data object ToggleFullScreen : MediaPlayerUiIntent()
    data object OpenWithOtherPlayer : MediaPlayerUiIntent()
    data object GoBack : MediaPlayerUiIntent()
    data class UpdateProgress(val position: Long, val duration: Long) : MediaPlayerUiIntent()
}
