package cc.kafuu.bilidownload.common.audio.spectrum

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import cc.kafuu.bilidownload.common.audio.kissfft.KissFft
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class RealtimeSpectrumAnalyzer(
    private val mFftSize: Int = 2048,
    private val mSpectrumBars: Int = 96,
    private val mWaveformSamples: Int = 160
) {
    private val mLock = Any()
    private val mFft = KissFft(mFftSize)
    private val mWindow = FloatArray(mFftSize) { index ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * index / (mFftSize - 1)))).toFloat()
    }
    private val mFftOutput = FloatArray(mFftSize * 2)
    private val mSpectrumScratch = FloatArray(mSpectrumBars)
    private val mSmoothedSpectrum = FloatArray(mSpectrumBars) { MIN_DISPLAY_VALUE }
    private val mBandStartIndexes = createBandStartIndexes()
    private val mBandEndIndexes = createBandEndIndexes()
    private val mBandFactors = createBandFactors()
    private val mFftSamples = FloatArray(mFftSize)
    private val mWaveformScratch = FloatArray(mWaveformSamples)
    private val mWaveformBuckets = FloatArray(mWaveformSamples)
    private val mWaveformBucketSerials = LongArray(mWaveformSamples) { Long.MIN_VALUE }

    private var mAppendScratch = FloatArray(0)
    private var mSampleRate = DEFAULT_SAMPLE_RATE
    private var mRingSamples = FloatArray(max(mFftSize * 4, DEFAULT_SAMPLE_RATE * RING_SECONDS))
    private var mWriteIndex = 0
    private var mAvailableSamples = 0
    private var mTotalSamples = 0L
    private var mTimedSegmentFirstSample = TIMED_SAMPLE_UNSET
    private var mTimedSegmentFirstTimeUs = C.TIME_UNSET
    private var mTimedSegmentLastTimeUs = C.TIME_UNSET

    fun configure(sampleRate: Int) {
        val nextSampleRate = sampleRate.coerceAtLeast(1)
        synchronized(mLock) {
            if (this.mSampleRate == nextSampleRate && mRingSamples.isNotEmpty()) return
            this.mSampleRate = nextSampleRate
            mRingSamples = FloatArray(max(mFftSize * 4, nextSampleRate * RING_SECONDS))
            clearLocked()
        }
    }

    fun clear() {
        synchronized(mLock) {
            clearLocked()
        }
    }

    @OptIn(UnstableApi::class)
    fun appendPcm(
        inputBuffer: ByteBuffer,
        sampleRate: Int,
        channelCount: Int,
        encoding: Int
    ) {
        configure(sampleRate)
        val sampleCount = decodePcmToScratch(inputBuffer, channelCount, encoding)
        if (sampleCount <= 0) return

        synchronized(mLock) {
            appendSamplesLocked(
                samples = mAppendScratch,
                count = sampleCount,
                startTimeUs = C.TIME_UNSET,
                sourceSampleRate = sampleRate.coerceAtLeast(1)
            )
        }
    }

    @OptIn(UnstableApi::class)
    fun appendTimestampedPcm(
        inputBuffer: ByteBuffer,
        startTimeUs: Long,
        sampleRate: Int,
        channelCount: Int,
        encoding: Int
    ) {
        configure(sampleRate)
        val sampleCount = decodePcmToScratch(inputBuffer, channelCount, encoding)
        if (sampleCount <= 0) return

        synchronized(mLock) {
            appendSamplesLocked(
                samples = mAppendScratch,
                count = sampleCount,
                startTimeUs = startTimeUs,
                sourceSampleRate = sampleRate.coerceAtLeast(1)
            )
        }
    }

    fun createFrame(
        positionMs: Long,
        includeSpectrum: Boolean = true,
        includeWaveform: Boolean = true
    ): RealtimeSpectrumFrame {
        var currentSampleRate = DEFAULT_SAMPLE_RATE
        var spectrum = EMPTY_FLOAT_ARRAY
        var waveform = EMPTY_FLOAT_ARRAY
        val visualPlaybackTimeUs = positionMsToVisualUs(positionMs)

        if (includeSpectrum) {
            synchronized(mLock) {
                currentSampleRate = mSampleRate
                val endTimeUs = playbackTimeUsToCapturedTimeUsLocked(visualPlaybackTimeUs)
                val copiedTimedSamples = copyTimedSamplesLocked(
                    target = mFftSamples,
                    count = mFftSize,
                    endTimeUs = endTimeUs
                )
                if (!copiedTimedSamples && hasTimedSamplesLocked()) {
                    val copiedNearestSamples = copyNearestPastTimedSamplesLocked(
                        target = mFftSamples,
                        count = mFftSize,
                        endTimeUs = endTimeUs
                    )
                    if (!copiedNearestSamples) copyLatestSamplesLocked(mFftSamples, mFftSize)
                } else if (!copiedTimedSamples) {
                    copyLatestSamplesLocked(mFftSamples, mFftSize)
                }
            }
            spectrum = createSpectrum(mFftSamples).copyOf()
        }

        if (includeWaveform) {
            synchronized(mLock) {
                currentSampleRate = mSampleRate
                val endTimeUs = playbackTimeUsToCapturedTimeUsLocked(visualPlaybackTimeUs)
                val copiedTimedWaveform = copyTimedWaveformLocked(
                    target = mWaveformScratch,
                    endTimeUs = endTimeUs
                )
                if (!copiedTimedWaveform && hasTimedSamplesLocked()) {
                    val copiedNearestWaveform = copyNearestPastTimedWaveformLocked(
                        target = mWaveformScratch,
                        endTimeUs = endTimeUs
                    )
                    if (!copiedNearestWaveform) copyWaveformBucketsLocked(mWaveformScratch)
                } else if (!copiedTimedWaveform) {
                    copyWaveformBucketsLocked(mWaveformScratch)
                }
            }
            waveform = mWaveformScratch.copyOf()
        }

        if (!includeSpectrum && !includeWaveform) {
            synchronized(mLock) {
                currentSampleRate = mSampleRate
            }
        }

        return RealtimeSpectrumFrame(
            positionMs = positionMs,
            sampleRate = currentSampleRate,
            spectrum = spectrum,
            waveform = waveform
        )
    }

    @OptIn(UnstableApi::class)
    private fun decodePcmToScratch(
        inputBuffer: ByteBuffer,
        channelCount: Int,
        encoding: Int
    ): Int {
        val safeChannelCount = channelCount.coerceAtLeast(1)
        val bytesPerFrame = bytesPerSample(encoding) * safeChannelCount
        if (bytesPerFrame <= 0 || inputBuffer.remaining() < bytesPerFrame) return 0

        val buffer = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val frameCapacity = buffer.remaining() / bytesPerFrame
        ensureAppendScratchCapacity(frameCapacity)
        return when (encoding) {
            C.ENCODING_PCM_FLOAT -> decodeFloatPcm(buffer, safeChannelCount, mAppendScratch)
            C.ENCODING_PCM_8BIT -> decode8BitPcm(buffer, safeChannelCount, mAppendScratch)
            C.ENCODING_PCM_24BIT -> decode24BitPcm(buffer, safeChannelCount, mAppendScratch)
            C.ENCODING_PCM_32BIT -> decode32BitPcm(buffer, safeChannelCount, mAppendScratch)
            else -> decode16BitPcm(buffer, safeChannelCount, mAppendScratch)
        }
    }

    private fun ensureAppendScratchCapacity(size: Int) {
        if (mAppendScratch.size < size) {
            mAppendScratch = FloatArray(size)
        }
    }

    private fun decode16BitPcm(
        buffer: ByteBuffer,
        channelCount: Int,
        output: FloatArray
    ): Int {
        val frameSize = channelCount * BYTES_PER_SHORT_SAMPLE
        var outputIndex = 0
        while (buffer.remaining() >= frameSize && outputIndex < output.size) {
            var mixed = 0f
            var channelIndex = 0
            while (channelIndex < channelCount) {
                mixed += buffer.short / SHORT_SAMPLE_SCALE
                channelIndex++
            }
            output[outputIndex++] = mixed / channelCount
        }
        return outputIndex
    }

    private fun decodeFloatPcm(
        buffer: ByteBuffer,
        channelCount: Int,
        output: FloatArray
    ): Int {
        val frameSize = channelCount * BYTES_PER_FLOAT_SAMPLE
        var outputIndex = 0
        while (buffer.remaining() >= frameSize && outputIndex < output.size) {
            var mixed = 0f
            var channelIndex = 0
            while (channelIndex < channelCount) {
                mixed += buffer.float.coerceIn(-1f, 1f)
                channelIndex++
            }
            output[outputIndex++] = mixed / channelCount
        }
        return outputIndex
    }

    private fun decode8BitPcm(
        buffer: ByteBuffer,
        channelCount: Int,
        output: FloatArray
    ): Int {
        val frameSize = channelCount
        var outputIndex = 0
        while (buffer.remaining() >= frameSize && outputIndex < output.size) {
            var mixed = 0f
            var channelIndex = 0
            while (channelIndex < channelCount) {
                mixed += ((buffer.get().toInt() and 0xff) - 128) / BYTE_SAMPLE_SCALE
                channelIndex++
            }
            output[outputIndex++] = mixed / channelCount
        }
        return outputIndex
    }

    private fun decode24BitPcm(
        buffer: ByteBuffer,
        channelCount: Int,
        output: FloatArray
    ): Int {
        val frameSize = channelCount * BYTES_PER_24BIT_SAMPLE
        var outputIndex = 0
        while (buffer.remaining() >= frameSize && outputIndex < output.size) {
            var mixed = 0f
            var channelIndex = 0
            while (channelIndex < channelCount) {
                val byte0 = buffer.get().toInt() and 0xff
                val byte1 = buffer.get().toInt() and 0xff
                val byte2 = buffer.get().toInt() and 0xff
                val rawSample = byte0 or (byte1 shl 8) or (byte2 shl 16)
                val signedSample = if (rawSample and SIGN_24BIT_MASK != 0) {
                    rawSample or SIGN_EXTEND_24BIT
                } else {
                    rawSample
                }
                mixed += signedSample / SAMPLE_24BIT_SCALE
                channelIndex++
            }
            output[outputIndex++] = mixed / channelCount
        }
        return outputIndex
    }

    private fun decode32BitPcm(
        buffer: ByteBuffer,
        channelCount: Int,
        output: FloatArray
    ): Int {
        val frameSize = channelCount * BYTES_PER_32BIT_SAMPLE
        var outputIndex = 0
        while (buffer.remaining() >= frameSize && outputIndex < output.size) {
            var mixed = 0f
            var channelIndex = 0
            while (channelIndex < channelCount) {
                mixed += buffer.int / SAMPLE_32BIT_SCALE
                channelIndex++
            }
            output[outputIndex++] = mixed / channelCount
        }
        return outputIndex
    }

    private fun appendSamplesLocked(
        samples: FloatArray,
        count: Int,
        startTimeUs: Long,
        sourceSampleRate: Int
    ) {
        if (startTimeUs != C.TIME_UNSET && count > 0) {
            updateTimedSegmentLocked(startTimeUs, count, sourceSampleRate)
        }
        for (index in 0 until count) {
            addSampleLocked(samples[index])
        }
    }

    private fun addSampleLocked(sample: Float) {
        val safeSample = sample.coerceIn(-1f, 1f)
        mRingSamples[mWriteIndex] = safeSample
        mWriteIndex = (mWriteIndex + 1) % mRingSamples.size
        if (mAvailableSamples < mRingSamples.size) mAvailableSamples++
        updateWaveformBucketLocked(abs(safeSample))
        mTotalSamples++
    }

    private fun updateTimedSegmentLocked(
        startTimeUs: Long,
        count: Int,
        sourceSampleRate: Int
    ) {
        val expectedStartTimeUs = if (mTimedSegmentLastTimeUs == C.TIME_UNSET) {
            C.TIME_UNSET
        } else {
            mTimedSegmentLastTimeUs + MICROS_PER_SECOND / sourceSampleRate
        }
        if (
            mTimedSegmentFirstTimeUs == C.TIME_UNSET ||
            expectedStartTimeUs == C.TIME_UNSET ||
            abs(startTimeUs - expectedStartTimeUs) > TIMESTAMP_DISCONTINUITY_US
        ) {
            mTimedSegmentFirstSample = mTotalSamples
            mTimedSegmentFirstTimeUs = startTimeUs
        }
        mTimedSegmentLastTimeUs =
            startTimeUs + (count - 1).toLong() * MICROS_PER_SECOND / sourceSampleRate
    }

    private fun updateWaveformBucketLocked(amplitude: Float) {
        val bucketSize = waveformBucketSizeLocked()
        val serial = mTotalSamples / bucketSize
        val slot = (serial % mWaveformSamples).toInt()
        if (mWaveformBucketSerials[slot] != serial) {
            mWaveformBucketSerials[slot] = serial
            mWaveformBuckets[slot] = 0f
        }
        if (amplitude > mWaveformBuckets[slot]) {
            mWaveformBuckets[slot] = amplitude.coerceIn(0f, 1f)
        }
    }

    private fun copyTimedSamplesLocked(
        target: FloatArray,
        count: Int,
        endTimeUs: Long
    ): Boolean {
        target.fill(0f)
        if (mAvailableSamples == 0 || endTimeUs == C.TIME_UNSET) return false

        val sourceSampleRate = mSampleRate.coerceAtLeast(1)
        val windowDurationUs = count.toLong() * MICROS_PER_SECOND / sourceSampleRate
        val startTimeUs = (endTimeUs - windowDurationUs).coerceAtLeast(0L)
        val startSampleIndex = sampleIndexForTimeLocked(startTimeUs) ?: return false
        val oldestSampleIndex = mTotalSamples - mAvailableSamples
        val latestSampleIndexExclusive = mTotalSamples
        var copiedSamples = 0

        for (targetIndex in 0 until count) {
            val sampleIndex = startSampleIndex + targetIndex
            if (sampleIndex in oldestSampleIndex until latestSampleIndexExclusive) {
                target[targetIndex] = mRingSamples[ringIndexForSampleIndex(sampleIndex)]
                copiedSamples++
            }
        }

        return copiedSamples >= count / MIN_TIMED_SAMPLE_DIVISOR
    }

    private fun copyTimedWaveformLocked(target: FloatArray, endTimeUs: Long): Boolean {
        target.fill(0f)
        if (mAvailableSamples == 0 || endTimeUs == C.TIME_UNSET) return false

        val startTimeUs = (endTimeUs - WAVEFORM_WINDOW_US).coerceAtLeast(0L)
        val startSampleIndex = sampleIndexForTimeLocked(startTimeUs) ?: return false
        val endSampleIndex = sampleIndexForTimeLocked(endTimeUs) ?: return false
        val durationSamples = (endSampleIndex - startSampleIndex).coerceAtLeast(1L)
        val oldestSampleIndex = mTotalSamples - mAvailableSamples
        val latestSampleIndexExclusive = mTotalSamples
        val firstSampleIndex = max(oldestSampleIndex, startSampleIndex)
        val lastSampleIndexExclusive = minOf(latestSampleIndexExclusive, endSampleIndex)
        var copiedSamples = 0

        var sampleIndex = firstSampleIndex
        while (sampleIndex < lastSampleIndexExclusive) {
            val targetIndex = ((sampleIndex - startSampleIndex) * target.size / durationSamples)
                .toInt()
                .coerceIn(target.indices)
            val amplitude = abs(mRingSamples[ringIndexForSampleIndex(sampleIndex)])
            if (amplitude > target[targetIndex]) {
                target[targetIndex] = amplitude.coerceIn(0f, 1f)
            }
            copiedSamples++
            sampleIndex++
        }

        return copiedSamples >= mSampleRate.coerceAtLeast(1) / MIN_WAVEFORM_SAMPLE_DIVISOR
    }

    private fun copyNearestPastTimedSamplesLocked(
        target: FloatArray,
        count: Int,
        endTimeUs: Long
    ): Boolean {
        val latestSafeTimeUs = latestCapturedTimeUsLocked().coerceAtMost(endTimeUs)
        if (latestSafeTimeUs == C.TIME_UNSET || latestSafeTimeUs < mTimedSegmentFirstTimeUs) {
            target.fill(0f)
            return false
        }
        return copyTimedSamplesLocked(
            target = target,
            count = count,
            endTimeUs = latestSafeTimeUs
        )
    }

    private fun copyNearestPastTimedWaveformLocked(target: FloatArray, endTimeUs: Long): Boolean {
        val latestSafeTimeUs = latestCapturedTimeUsLocked().coerceAtMost(endTimeUs)
        if (latestSafeTimeUs == C.TIME_UNSET || latestSafeTimeUs < mTimedSegmentFirstTimeUs) {
            target.fill(0f)
            return false
        }
        return copyTimedWaveformLocked(target = target, endTimeUs = latestSafeTimeUs)
    }

    private fun sampleIndexForTimeLocked(timeUs: Long): Long? {
        if (
            mTimedSegmentFirstSample == TIMED_SAMPLE_UNSET ||
            mTimedSegmentFirstTimeUs == C.TIME_UNSET
        ) {
            return null
        }
        val timeOffsetUs = timeUs - mTimedSegmentFirstTimeUs
        if (timeOffsetUs < -TIMESTAMP_DISCONTINUITY_US) return null
        val safeOffsetUs = timeOffsetUs.coerceAtLeast(0L)
        return mTimedSegmentFirstSample +
            safeOffsetUs * mSampleRate.coerceAtLeast(1) / MICROS_PER_SECOND
    }

    private fun hasTimedSamplesLocked(): Boolean {
        return mTimedSegmentFirstSample != TIMED_SAMPLE_UNSET &&
            mTimedSegmentFirstTimeUs != C.TIME_UNSET
    }

    private fun playbackTimeUsToCapturedTimeUsLocked(playbackTimeUs: Long): Long {
        if (!hasTimedSamplesLocked()) return playbackTimeUs
        return if (playbackTimeUs + TIMESTAMP_DISCONTINUITY_US < mTimedSegmentFirstTimeUs) {
            mTimedSegmentFirstTimeUs + playbackTimeUs
        } else {
            playbackTimeUs
        }
    }

    private fun latestCapturedTimeUsLocked(): Long {
        return mTimedSegmentLastTimeUs
    }

    private fun ringIndexForSampleIndex(sampleIndex: Long): Int {
        return (sampleIndex % mRingSamples.size).toInt()
    }

    private fun copyLatestSamplesLocked(target: FloatArray, count: Int) {
        target.fill(0f)
        if (mAvailableSamples == 0) return

        val samplesToCopy = minOf(count, mAvailableSamples)
        val startOffset = count - samplesToCopy
        var offset = 0
        while (offset < samplesToCopy) {
            val ringIndex = ringIndexFromLatestLocked(samplesToCopy - offset)
            target[startOffset + offset] = mRingSamples[ringIndex]
            offset++
        }
    }

    private fun copyWaveformBucketsLocked(target: FloatArray) {
        target.fill(0f)
        if (mTotalSamples <= 0L) return

        val bucketSize = waveformBucketSizeLocked()
        val latestSerial = (mTotalSamples - 1L) / bucketSize
        val firstSerial = latestSerial - target.size + 1L
        for (index in target.indices) {
            val serial = firstSerial + index
            if (serial < 0L) continue
            val slot = (serial % mWaveformSamples).toInt()
            if (mWaveformBucketSerials[slot] == serial) {
                target[index] = mWaveformBuckets[slot]
            }
        }
    }

    private fun waveformBucketSizeLocked(): Int {
        return (mSampleRate * WAVEFORM_SECONDS / mWaveformSamples).coerceAtLeast(1)
    }

    private fun ringIndexFromLatestLocked(samplesBehindLatest: Int): Int {
        val latestExclusive = mWriteIndex
        val rawIndex = latestExclusive - samplesBehindLatest
        return if (rawIndex >= 0) rawIndex else rawIndex + mRingSamples.size
    }

    private fun positionMsToUs(positionMs: Long): Long {
        return positionMs.coerceAtLeast(0L) * MILLIS_TO_MICROS
    }

    private fun positionMsToVisualUs(positionMs: Long): Long {
        return positionMsToUs(positionMs - VISUAL_OUTPUT_DELAY_MS)
    }

    private fun clearLocked() {
        mRingSamples.fill(0f)
        mWaveformBuckets.fill(0f)
        mWaveformBucketSerials.fill(Long.MIN_VALUE)
        mSmoothedSpectrum.fill(MIN_DISPLAY_VALUE)
        mWriteIndex = 0
        mAvailableSamples = 0
        mTotalSamples = 0L
        mTimedSegmentFirstSample = TIMED_SAMPLE_UNSET
        mTimedSegmentFirstTimeUs = C.TIME_UNSET
        mTimedSegmentLastTimeUs = C.TIME_UNSET
    }

    private fun createSpectrum(samples: FloatArray): FloatArray {
        mFft.transformReal(samples, mWindow, mFftOutput)

        for (bar in 0 until mSpectrumBars) {
            val startBin = mBandStartIndexes[bar]
            val endBin = mBandEndIndexes[bar]
            var powerSum = 0f
            for (bin in startBin until endBin) {
                val src = bin * 2
                val real = mFftOutput[src]
                val imaginary = mFftOutput[src + 1]
                powerSum += real * real + imaginary * imaginary
            }

            val binCount = (endBin - startBin).coerceAtLeast(1)
            val averageMagnitude = sqrt(powerSum / binCount)
            val normalizedMagnitude = averageMagnitude / (mFftSize / 2f)
            val target = (normalizedMagnitude * mBandFactors[bar])
                .coerceIn(MIN_DISPLAY_VALUE, 1f)
            val current = mSmoothedSpectrum[bar]
            val next = if (target > current) {
                target
            } else {
                current * DECAY_FACTOR + target * (1f - DECAY_FACTOR)
            }
            mSmoothedSpectrum[bar] = next.coerceAtLeast(MIN_DISPLAY_VALUE)
            mSpectrumScratch[bar] = mSmoothedSpectrum[bar]
        }
        return mSpectrumScratch
    }

    private fun createBandStartIndexes(): IntArray {
        val effectiveBinCount = effectiveBinCount()
        return IntArray(mSpectrumBars) { index ->
            effectiveBinCount.toDouble()
                .pow(index.toDouble() / mSpectrumBars.toDouble())
                .toInt()
                .coerceIn(1, effectiveBinCount)
        }
    }

    private fun createBandEndIndexes(): IntArray {
        val effectiveBinCount = effectiveBinCount()
        return IntArray(mSpectrumBars) { index ->
            val startIndex = mBandStartIndexes.getOrElse(index) {
                effectiveBinCount.toDouble()
                    .pow(index.toDouble() / mSpectrumBars.toDouble())
                    .toInt()
                    .coerceIn(1, effectiveBinCount)
            }
            val nextIndex = effectiveBinCount.toDouble()
                .pow((index + 1).toDouble() / mSpectrumBars.toDouble())
                .toInt()
                .coerceIn(1, effectiveBinCount)
            if (nextIndex <= startIndex) startIndex + 1 else nextIndex
        }
    }

    private fun createBandFactors(): FloatArray {
        return FloatArray(mSpectrumBars) { index ->
            (1f + index * HIGH_BAND_LINEAR_GAIN + index * index * HIGH_BAND_QUADRATIC_GAIN) *
                MAGNITUDE_GAIN
        }
    }

    private fun effectiveBinCount(): Int {
        val halfFftSize = mFftSize / 2
        return (halfFftSize * EFFECTIVE_BIN_RATIO).roundToInt()
            .coerceIn(mSpectrumBars, halfFftSize)
    }

    @OptIn(UnstableApi::class)
    private fun bytesPerSample(encoding: Int): Int {
        return when (encoding) {
            C.ENCODING_PCM_FLOAT -> BYTES_PER_FLOAT_SAMPLE
            C.ENCODING_PCM_8BIT -> BYTES_PER_8BIT_SAMPLE
            C.ENCODING_PCM_24BIT -> BYTES_PER_24BIT_SAMPLE
            C.ENCODING_PCM_32BIT -> BYTES_PER_32BIT_SAMPLE
            else -> BYTES_PER_SHORT_SAMPLE
        }
    }

    companion object {
        private val EMPTY_FLOAT_ARRAY = FloatArray(0)
        private const val DEFAULT_SAMPLE_RATE = 44_100
        private const val RING_SECONDS = 6
        private const val WAVEFORM_SECONDS = 1
        private const val VISUAL_OUTPUT_DELAY_MS = 120L
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val MILLIS_TO_MICROS = 1_000L
        private const val WAVEFORM_WINDOW_US = WAVEFORM_SECONDS * MICROS_PER_SECOND
        private const val MIN_TIMED_SAMPLE_DIVISOR = 4
        private const val MIN_WAVEFORM_SAMPLE_DIVISOR = 10
        private const val TIMESTAMP_DISCONTINUITY_US = 50_000L
        private const val TIMED_SAMPLE_UNSET = Long.MIN_VALUE
        private const val BYTES_PER_8BIT_SAMPLE = 1
        private const val BYTES_PER_SHORT_SAMPLE = 2
        private const val BYTES_PER_FLOAT_SAMPLE = 4
        private const val BYTES_PER_24BIT_SAMPLE = 3
        private const val BYTES_PER_32BIT_SAMPLE = 4
        private const val BYTE_SAMPLE_SCALE = 128f
        private const val SHORT_SAMPLE_SCALE = 32768f
        private const val SAMPLE_24BIT_SCALE = 8_388_608f
        private const val SAMPLE_32BIT_SCALE = 2_147_483_648f
        private const val SIGN_24BIT_MASK = 0x80_0000
        private const val SIGN_EXTEND_24BIT = -0x100_0000
        private const val EFFECTIVE_BIN_RATIO = 0.45f
        private const val HIGH_BAND_LINEAR_GAIN = 0.05f
        private const val HIGH_BAND_QUADRATIC_GAIN = 0.002f
        private const val MAGNITUDE_GAIN = 8f
        private const val DECAY_FACTOR = 0.82f
        private const val MIN_DISPLAY_VALUE = 0.15f
    }
}
