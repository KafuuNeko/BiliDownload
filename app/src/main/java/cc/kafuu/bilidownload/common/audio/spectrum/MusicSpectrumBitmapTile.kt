package cc.kafuu.bilidownload.common.audio.spectrum

import android.graphics.Bitmap

data class MusicSpectrumBitmapTile(
    val startMs: Long,
    val durationMs: Long,
    val bitmap: Bitmap
)
