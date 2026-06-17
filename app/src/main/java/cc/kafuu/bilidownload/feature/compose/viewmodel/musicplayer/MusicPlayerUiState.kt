package cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer

import android.graphics.Bitmap
import androidx.media3.exoplayer.ExoPlayer
import cc.kafuu.bilidownload.common.audio.spectrum.MusicSpectrumBitmapTile
import cc.kafuu.bilidownload.common.audio.spectrum.MusicSpectrumData
import cc.kafuu.bilidownload.common.audio.spectrum.RealtimeSpectrumFrame

sealed class MusicPlayerUiState {
    data object None : MusicPlayerUiState()

    data class Playing(
        val title: String,
        val filePath: String,
        val mimeType: String,
        val player: ExoPlayer,
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val playbackSpeed: Float = 1.0f,
        val selectedPlaybackSpeed: Float = 1.0f,
        val isSeekBarDragging: Boolean = false,
        val spectrumMode: MusicSpectrumMode = MusicSpectrumMode.Spectrogram,
        val spectrumData: MusicSpectrumData? = null,
        val realtimeSpectrumFrame: RealtimeSpectrumFrame? = null,
        val spectrumOverviewBitmap: Bitmap? = null,
        val spectrogramTiles: List<MusicSpectrumBitmapTile> = emptyList(),
        val isAnalyzingSpectrum: Boolean = false,
        val spectrumProgress: Float = 0f,
        val spectrumError: String? = null,
        val spectrogramWindowStartMs: Long = 0L,
        val isSpectrogramFollowingPlayback: Boolean = true
    ) : MusicPlayerUiState()

    data class Error(val message: String) : MusicPlayerUiState()
}
