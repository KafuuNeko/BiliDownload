package cc.kafuu.bilidownload.common.audio.spectrum

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class TimestampedPcmAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink,
    private val analyzer: RealtimeSpectrumAnalyzer
) : MediaCodecAudioRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink
) {
    private var mOutputSampleRate = FORMAT_VALUE_UNSET
    private var mOutputChannelCount = FORMAT_VALUE_UNSET
    private var mOutputEncoding = C.ENCODING_PCM_16BIT
    private var mLastCapturedBufferIndex = Int.MIN_VALUE
    private var mLastCapturedPresentationTimeUs = C.TIME_UNSET

    @Throws(ExoPlaybackException::class)
    override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        mOutputSampleRate = mediaFormat?.getIntegerIfPresent(MediaFormat.KEY_SAMPLE_RATE)
            ?: format.sampleRate
        mOutputChannelCount = mediaFormat?.getIntegerIfPresent(MediaFormat.KEY_CHANNEL_COUNT)
            ?: format.channelCount
        mOutputEncoding = if (
            Util.SDK_INT >= 24 &&
            mediaFormat?.containsKey(MediaFormat.KEY_PCM_ENCODING) == true
        ) {
            mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            format.pcmEncoding
        }

        if (mOutputSampleRate > 0) {
            analyzer.configure(mOutputSampleRate)
        }

        super.onOutputFormatChanged(format, mediaFormat)
    }

    @Throws(ExoPlaybackException::class)
    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        analyzer.clear()
        resetCaptureGuard()
        super.onPositionReset(positionUs, joining)
    }

    @Throws(ExoPlaybackException::class)
    override fun onDisabled() {
        analyzer.clear()
        resetCaptureGuard()
        super.onDisabled()
    }

    @Throws(ExoPlaybackException::class)
    override fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodecAdapter?,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferFlags: Int,
        sampleCount: Int,
        bufferPresentationTimeUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean,
        format: Format
    ): Boolean {
        capturePcmBuffer(
            buffer = buffer,
            bufferIndex = bufferIndex,
            bufferFlags = bufferFlags,
            bufferPresentationTimeUs = bufferPresentationTimeUs,
            isDecodeOnlyBuffer = isDecodeOnlyBuffer,
            isLastBuffer = isLastBuffer,
            format = format
        )

        return super.processOutputBuffer(
            positionUs,
            elapsedRealtimeUs,
            codec,
            buffer,
            bufferIndex,
            bufferFlags,
            sampleCount,
            bufferPresentationTimeUs,
            isDecodeOnlyBuffer,
            isLastBuffer,
            format
        )
    }

    private fun capturePcmBuffer(
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferFlags: Int,
        bufferPresentationTimeUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean,
        format: Format
    ) {
        if (buffer == null || buffer.remaining() <= 0) return
        if (bufferPresentationTimeUs == C.TIME_UNSET) return
        if (isDecodeOnlyBuffer || isLastBuffer) return
        if ((bufferFlags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) return
        if (
            bufferIndex == mLastCapturedBufferIndex &&
            bufferPresentationTimeUs == mLastCapturedPresentationTimeUs
        ) {
            return
        }

        val sampleRate = mOutputSampleRate.takeIf { it > 0 } ?: format.sampleRate
        val channelCount = mOutputChannelCount.takeIf { it > 0 } ?: format.channelCount
        if (sampleRate <= 0 || channelCount <= 0) return

        val input = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        analyzer.appendTimestampedPcm(
            inputBuffer = input,
            startTimeUs = bufferPresentationTimeUs,
            sampleRate = sampleRate,
            channelCount = channelCount,
            encoding = mOutputEncoding
        )
        mLastCapturedBufferIndex = bufferIndex
        mLastCapturedPresentationTimeUs = bufferPresentationTimeUs
    }

    private fun resetCaptureGuard() {
        mLastCapturedBufferIndex = Int.MIN_VALUE
        mLastCapturedPresentationTimeUs = C.TIME_UNSET
    }

    private fun MediaFormat.getIntegerIfPresent(key: String): Int? {
        return if (containsKey(key)) getInteger(key) else null
    }

    private companion object {
        private const val FORMAT_VALUE_UNSET = -1
    }
}
