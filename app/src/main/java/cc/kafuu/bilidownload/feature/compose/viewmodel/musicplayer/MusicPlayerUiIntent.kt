package cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer

import android.content.Context

sealed class MusicPlayerUiIntent {
    data class Init(
        val context: Context,
        val filePath: String,
        val title: String,
        val mimeType: String,
        val contentUri: String?
    ) : MusicPlayerUiIntent()

    data object TogglePlayPause : MusicPlayerUiIntent()
    data class SetPlaybackSpeed(val speed: Float) : MusicPlayerUiIntent()
    data class SetSpectrumMode(val mode: MusicSpectrumMode) : MusicPlayerUiIntent()
    data class SetSpectrogramWindow(val startMs: Long, val followPlayback: Boolean = false) : MusicPlayerUiIntent()
    data class SeekTo(val position: Long, val syncSpectrogramWindow: Boolean = true) : MusicPlayerUiIntent()
    data object SeekBarDragStart : MusicPlayerUiIntent()
    data class SeekBarDragEnd(val position: Long, val syncSpectrogramWindow: Boolean = true) : MusicPlayerUiIntent()
    data object OpenWithOtherPlayer : MusicPlayerUiIntent()
    data object GoBack : MusicPlayerUiIntent()
    data class UpdateProgress(val position: Long, val duration: Long) : MusicPlayerUiIntent()
}
