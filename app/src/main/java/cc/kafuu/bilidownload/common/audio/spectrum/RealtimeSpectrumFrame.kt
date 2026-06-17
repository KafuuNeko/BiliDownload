package cc.kafuu.bilidownload.common.audio.spectrum

data class RealtimeSpectrumFrame(
    val positionMs: Long,
    val sampleRate: Int,
    val spectrum: FloatArray,
    val waveform: FloatArray
)
