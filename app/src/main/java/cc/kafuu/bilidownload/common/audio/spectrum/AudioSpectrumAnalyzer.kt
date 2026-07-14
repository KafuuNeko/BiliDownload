package cc.kafuu.bilidownload.common.audio.spectrum

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import cc.kafuu.bilidownload.common.audio.kissfft.KissFft
import cc.kafuu.bilidownload.common.audio.pcm.PcmDecoder
import cc.kafuu.bilidownload.common.audio.pcm.PcmEncodingMapper
import cc.kafuu.bilidownload.common.audio.pcm.PcmSampleEncoding
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 离线解码完整音频文件并生成可持久展示的频谱数据。
 *
 * MediaCodec 输出的各种 PCM 位深统一交给 [PcmDecoder] 归一化，避免离线分析与实时频谱
 * 对同一音频产生不同振幅结果。
 */
class AudioSpectrumAnalyzer(
    private val mFftSize: Int = 4096,
    private val mHopSize: Int = 512,
    private val mSpectrumColumns: Int = 960,
    private val mSpectrumRows: Int = 160
) {
    /**
     * 分析指定文件的首条音轨。
     *
     * @param filePath MediaExtractor 可读取的本地文件路径。
     * @param onProgress 接收 `0..1` 的分析进度；协程取消会及时终止解码。
     * @return 包含频谱矩阵及时间信息的数据。
     */
    suspend fun analyze(
        filePath: String,
        onProgress: (Float) -> Unit = {}
    ): MusicSpectrumData {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(filePath)
            val trackIndex = findAudioTrack(extractor)
            require(trackIndex >= 0) { "No audio track found." }

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Unknown audio MIME type.")
            val inputSampleRate = inputFormat.getIntegerOrDefault(
                MediaFormat.KEY_SAMPLE_RATE,
                44_100
            )
            val inputChannelCount = inputFormat.getIntegerOrDefault(
                MediaFormat.KEY_CHANNEL_COUNT,
                1
            )
            val durationUs = inputFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
            val targetColumns = spectrumColumnsForDuration(durationUs)

            val collector = SpectrumCollector(
                fftSize = mFftSize,
                hopSize = mHopSize,
                columns = targetColumns,
                rows = mSpectrumRows,
                sampleRate = inputSampleRate,
                durationUs = durationUs
            )

            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            decodeToCollector(
                extractor = extractor,
                codec = codec,
                collector = collector,
                initialChannelCount = inputChannelCount,
                onProgress = onProgress
            )

            onProgress(1f)
            return collector.createData()
        } finally {
            codec?.runCatching {
                stop()
                release()
            }
            extractor.release()
        }
    }

    private suspend fun decodeToCollector(
        extractor: MediaExtractor,
        codec: MediaCodec,
        collector: SpectrumCollector,
        initialChannelCount: Int,
        onProgress: (Float) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var channelCount = initialChannelCount.coerceAtLeast(1)
        var pcmEncoding = PcmSampleEncoding.PCM_16_BIT
        var lastProgressSample = 0L

        while (!outputEnded) {
            currentCoroutineContext().ensureActive()

            if (!inputEnded) {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    val sampleSize = inputBuffer?.let { buffer ->
                        buffer.clear()
                        extractor.readSampleData(buffer, 0)
                    } ?: -1

                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime.coerceAtLeast(0L),
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = codec.outputFormat
                    channelCount = outputFormat.getIntegerOrDefault(
                        MediaFormat.KEY_CHANNEL_COUNT,
                        channelCount
                    ).coerceAtLeast(1)
                    pcmEncoding = PcmEncodingMapper.fromPlatform(
                        outputFormat.getIntegerOrDefault(
                            MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                    )
                }

                else -> if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (
                        outputBuffer != null &&
                        bufferInfo.size > 0 &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        processPcmBuffer(
                            outputBuffer = outputBuffer,
                            info = bufferInfo,
                            channelCount = channelCount,
                            pcmEncoding = pcmEncoding,
                            collector = collector
                        )
                        if (collector.samplesSeen - lastProgressSample > collector.sampleRate) {
                            lastProgressSample = collector.samplesSeen
                            onProgress(collector.progress())
                        }
                    }

                    outputEnded =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    /** 将一块解码后的 PCM 数据转为单声道样本并送入频谱收集器。 */
    private fun processPcmBuffer(
        outputBuffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channelCount: Int,
        pcmEncoding: PcmSampleEncoding,
        collector: SpectrumCollector
    ) {
        outputBuffer.position(info.offset)
        outputBuffer.limit(info.offset + info.size)
        val buffer = outputBuffer.slice()
        PcmDecoder.decodeToMono(buffer, channelCount, pcmEncoding).forEach { sample ->
            collector.addSample(sample)
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mimeType.startsWith("audio/")) return i
        }
        return -1
    }

    private fun spectrumColumnsForDuration(durationUs: Long): Int {
        if (durationUs <= 0L) return mSpectrumColumns
        val durationSeconds = (durationUs / 1_000_000L).coerceAtLeast(1L)
        return (durationSeconds * SPECTRUM_COLUMNS_PER_SECOND)
            .coerceIn(mSpectrumColumns.toLong(), MAX_SPECTRUM_COLUMNS.toLong())
            .toInt()
    }

    private class SpectrumCollector(
        private val fftSize: Int,
        private val hopSize: Int,
        private val columns: Int,
        private val rows: Int,
        val sampleRate: Int,
        durationUs: Long
    ) {
        private val durationSamples = if (durationUs > 0L) {
            max(1L, durationUs * sampleRate / 1_000_000L)
        } else {
            0L
        }
        private val durationMs = if (durationUs > 0L) durationUs / 1000L else 0L
        private val fft = KissFft(fftSize)
        private val window = FloatArray(fftSize) { index ->
            sin(PI * index / (fftSize - 1)).let { (it * it).toFloat() }
        }
        private val ring = FloatArray(fftSize)
        private val fftOutput = FloatArray(fftSize * 2)
        private val spectrum = FloatArray(columns * rows)
        private val waveform = FloatArray(columns)
        private val binRanges = createBinRanges(rows, fftSize, sampleRate)
        private var ringIndex = 0
        private var maxMagnitude = 0f
        var samplesSeen = 0L
            private set

        fun addSample(sample: Float) {
            val safeSample = sample.coerceIn(-1f, 1f)
            val column = sampleToColumn(samplesSeen)
            waveform[column] = max(waveform[column], abs(safeSample))

            ring[ringIndex] = safeSample
            ringIndex = (ringIndex + 1) % fftSize
            samplesSeen++

            if (samplesSeen >= fftSize && (samplesSeen - fftSize) % hopSize == 0L) {
                collectFrame(samplesSeen - fftSize)
            }
        }

        fun progress(): Float {
            if (durationSamples <= 0L) return 0f
            return (samplesSeen.toFloat() / durationSamples.toFloat()).coerceIn(0f, 1f)
        }

        fun createData(): MusicSpectrumData {
            val normalized = FloatArray(spectrum.size)
            val reference = maxMagnitude.coerceAtLeast(0.000001f)
            val scale = ln(LOG_SCALE + 1.0)
            for (i in spectrum.indices) {
                val ratio = (spectrum[i] / reference).coerceIn(0f, 1f)
                normalized[i] = (ln(1.0 + ratio * LOG_SCALE) / scale).toFloat()
            }

            return MusicSpectrumData(
                columns = columns,
                rows = rows,
                durationMs = durationMs,
                sampleRate = sampleRate,
                values = normalized,
                waveform = waveform
            )
        }

        private fun collectFrame(frameStartSample: Long) {
            fft.transformRealRing(ring, ringIndex, window, fftOutput)

            val column = sampleToColumn(frameStartSample)
            for (row in 0 until rows) {
                val range = binRanges[row]
                var maxPower = 0f
                for (bin in range.first..range.last) {
                    val src = bin * 2
                    val real = fftOutput[src]
                    val imaginary = fftOutput[src + 1]
                    maxPower = max(maxPower, real * real + imaginary * imaginary)
                }

                val magnitude = sqrt(maxPower)
                val index = row * columns + column
                if (magnitude > spectrum[index]) spectrum[index] = magnitude
                if (magnitude > maxMagnitude) maxMagnitude = magnitude
            }
        }

        private fun sampleToColumn(sampleIndex: Long): Int {
            if (columns <= 1) return 0
            val ratio = if (durationSamples > 0L) {
                sampleIndex.toFloat() / durationSamples.toFloat()
            } else {
                samplesSeen.toFloat() / (sampleRate * FALLBACK_DURATION_SECONDS).toFloat()
            }
            return (ratio.coerceIn(0f, 1f) * (columns - 1)).toInt()
        }

        private fun createBinRanges(
            rows: Int,
            fftSize: Int,
            sampleRate: Int
        ): Array<IntRange> {
            val nyquist = sampleRate / 2f
            val lastBin = fftSize / 2 - 1
            val minFrequency = 20f.coerceAtMost(nyquist)
            val maxFrequency = nyquist.coerceAtLeast(minFrequency + 1f)
            val minLog = ln(minFrequency.toDouble())
            val maxLog = ln(maxFrequency.toDouble())

            return Array(rows) { row ->
                val startRatio = row.toDouble() / rows.toDouble()
                val endRatio = (row + 1).toDouble() / rows.toDouble()
                val startFrequency = kotlin.math.exp(minLog + (maxLog - minLog) * startRatio)
                val endFrequency = kotlin.math.exp(minLog + (maxLog - minLog) * endRatio)
                val startBin = ((startFrequency / nyquist) * lastBin)
                    .roundToInt()
                    .coerceIn(1, lastBin)
                val endBin = ((endFrequency / nyquist) * lastBin)
                    .roundToInt()
                    .coerceIn(startBin, lastBin)
                startBin..endBin
            }
        }
    }

    companion object {
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val FALLBACK_DURATION_SECONDS = 240
        private const val LOG_SCALE = 64.0
        private const val SPECTRUM_COLUMNS_PER_SECOND = 4
        private const val MAX_SPECTRUM_COLUMNS = 7200
    }
}

private fun MediaFormat.getIntegerOrDefault(key: String, defaultValue: Int): Int {
    return if (containsKey(key)) getInteger(key) else defaultValue
}

private fun MediaFormat.getLongOrDefault(key: String, defaultValue: Long): Long {
    return if (containsKey(key)) getLong(key) else defaultValue
}
