package cc.kafuu.bilidownload.common.audio.spectrum

import android.graphics.Bitmap
import kotlin.math.roundToInt

class MusicSpectrumBitmapRenderer(
    private val mTileDurationMs: Long = DEFAULT_TILE_DURATION_MS,
    private val mTileWidth: Int = DEFAULT_TILE_WIDTH,
    private val mTileHeight: Int = DEFAULT_TILE_HEIGHT,
    private val mOverviewWidth: Int = DEFAULT_OVERVIEW_WIDTH,
    private val mOverviewHeight: Int = DEFAULT_OVERVIEW_HEIGHT
) {
    fun renderOverview(data: MusicSpectrumData): Bitmap {
        return renderBitmap(
            data = data,
            startMs = 0L,
            durationMs = data.durationMs.coerceAtLeast(1L),
            width = mOverviewWidth,
            height = mOverviewHeight
        )
    }

    fun renderTile(data: MusicSpectrumData, startMs: Long): MusicSpectrumBitmapTile {
        val safeStart = startMs.coerceIn(0L, data.durationMs.coerceAtLeast(0L))
        val safeDuration = if (data.durationMs <= 0L) {
            mTileDurationMs
        } else {
            (data.durationMs - safeStart).coerceIn(1L, mTileDurationMs)
        }

        return MusicSpectrumBitmapTile(
            startMs = safeStart,
            durationMs = safeDuration,
            bitmap = renderBitmap(
                data = data,
                startMs = safeStart,
                durationMs = safeDuration,
                width = mTileWidth,
                height = mTileHeight
            )
        )
    }

    fun tileStartFor(positionMs: Long): Long {
        if (positionMs <= 0L) return 0L
        return (positionMs / mTileDurationMs) * mTileDurationMs
    }

    fun requiredTileStarts(windowStartMs: Long, windowDurationMs: Long, durationMs: Long): List<Long> {
        if (durationMs <= 0L) return listOf(0L)
        val safeStart = windowStartMs.coerceIn(0L, durationMs)
        val safeEnd = (safeStart + windowDurationMs).coerceIn(0L, durationMs)
        val first = tileStartFor(safeStart)
        val last = tileStartFor(safeEnd)
        return if (first == last) {
            listOf(first)
        } else {
            listOf(first, last)
        }
    }

    private fun renderBitmap(
        data: MusicSpectrumData,
        startMs: Long,
        durationMs: Long,
        width: Int,
        height: Int
    ): Bitmap {
        val pixels = IntArray(width * height)
        val safeDuration = durationMs.coerceAtLeast(1L)
        for (x in 0 until width) {
            val ratioX = if (width <= 1) 0f else x.toFloat() / (width - 1).toFloat()
            val timeMs = startMs + (safeDuration * ratioX).roundToInt()
            val column = data.spectrumColumn(timeMs)
            for (y in 0 until height) {
                val row = ((height - 1 - y).toFloat() / height * data.rows)
                    .toInt()
                    .coerceIn(0, data.rows - 1)
                val value = data.spectrumValue(column, row)
                pixels[y * width + x] = heatColorArgb(value)
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun heatColorArgb(value: Float): Int {
        val safe = value.coerceIn(0f, 1f)
        val stops = intArrayOf(
            0xFF07111F.toInt(),
            0xFF184C8A.toInt(),
            0xFF7A3FC3.toInt(),
            0xFFFA7298.toInt(),
            0xFFFFB84D.toInt(),
            0xFFFFF4A4.toInt()
        )
        val positions = floatArrayOf(0f, 0.22f, 0.48f, 0.70f, 0.88f, 1f)

        for (index in 0 until positions.lastIndex) {
            val start = positions[index]
            val end = positions[index + 1]
            if (safe in start..end) {
                val ratio = (safe - start) / (end - start)
                return lerpArgb(stops[index], stops[index + 1], ratio)
            }
        }
        return stops.last()
    }

    private fun lerpArgb(startColor: Int, endColor: Int, ratio: Float): Int {
        val safeRatio = ratio.coerceIn(0f, 1f)
        val startA = startColor ushr 24 and 0xff
        val startR = startColor ushr 16 and 0xff
        val startG = startColor ushr 8 and 0xff
        val startB = startColor and 0xff
        val endA = endColor ushr 24 and 0xff
        val endR = endColor ushr 16 and 0xff
        val endG = endColor ushr 8 and 0xff
        val endB = endColor and 0xff

        val alpha = (startA + (endA - startA) * safeRatio).roundToInt()
        val red = (startR + (endR - startR) * safeRatio).roundToInt()
        val green = (startG + (endG - startG) * safeRatio).roundToInt()
        val blue = (startB + (endB - startB) * safeRatio).roundToInt()
        return alpha shl 24 or (red shl 16) or (green shl 8) or blue
    }

    companion object {
        const val DEFAULT_TILE_DURATION_MS = 60_000L
        private const val DEFAULT_TILE_WIDTH = 1536
        private const val DEFAULT_TILE_HEIGHT = 512
        private const val DEFAULT_OVERVIEW_WIDTH = 1536
        private const val DEFAULT_OVERVIEW_HEIGHT = 128
    }
}
