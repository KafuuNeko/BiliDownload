package cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer

import androidx.annotation.StringRes
import cc.kafuu.bilidownload.R

enum class MusicSpectrumMode(@StringRes val labelRes: Int) {
    Spectrogram(R.string.music_spectrum_mode_spectrogram),
    Realtime(R.string.music_spectrum_mode_realtime),
    Rhythm(R.string.music_spectrum_mode_rhythm),
    Waveform(R.string.music_spectrum_mode_waveform)
}
