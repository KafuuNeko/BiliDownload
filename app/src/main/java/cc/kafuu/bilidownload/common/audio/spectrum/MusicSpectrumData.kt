package cc.kafuu.bilidownload.common.audio.spectrum

data class MusicSpectrumData(
    val columns: Int,
    val rows: Int,
    val durationMs: Long,
    val sampleRate: Int,
    val values: FloatArray,
    val waveform: FloatArray
) {
    fun spectrumValue(column: Int, row: Int): Float {
        if (columns == 0 || rows == 0) return 0f
        val safeColumn = column.coerceIn(0, columns - 1)
        val safeRow = row.coerceIn(0, rows - 1)
        return values[safeRow * columns + safeColumn]
    }

    fun waveformValue(column: Int): Float {
        if (waveform.isEmpty()) return 0f
        return waveform[column.coerceIn(0, waveform.lastIndex)]
    }

    fun spectrumColumn(positionMs: Long): Int {
        if (columns == 0 || durationMs <= 0L) return 0
        val ratio = positionMs.toFloat() / durationMs.toFloat()
        return (ratio.coerceIn(0f, 1f) * (columns - 1)).toInt()
    }
}
