package cc.kafuu.bilidownload.common.audio.spectrum

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import cc.kafuu.bilidownload.common.audio.pcm.PcmEncodingMapper
import cc.kafuu.bilidownload.common.audio.pcm.PcmSampleEncoding
import java.nio.ByteBuffer

/**
 * 在保持 Media3 正常音频渲染的同时，将解码后的 PCM 和展示时间戳送入实时频谱分析器。
 *
 * 不受支持的 PCM 编码只会禁用本次频谱捕获，不会中断音频播放。
 */
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
    private var mOutputEncoding: PcmSampleEncoding? = PcmSampleEncoding.PCM_16_BIT
    private var mLastCapturedBufferIndex = Int.MIN_VALUE
    private var mLastCapturedPresentationTimeUs = C.TIME_UNSET

    @Throws(ExoPlaybackException::class)
    override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        mOutputSampleRate = mediaFormat?.getIntegerIfPresent(MediaFormat.KEY_SAMPLE_RATE)
            ?: format.sampleRate
        mOutputChannelCount = mediaFormat?.getIntegerIfPresent(MediaFormat.KEY_CHANNEL_COUNT)
            ?: format.channelCount
        val decoderOutputEncoding =
            if (
                Util.SDK_INT >= 24 &&
                mediaFormat?.containsKey(MediaFormat.KEY_PCM_ENCODING) == true
            ) {
                mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                null
            }

        mOutputEncoding = try {
            PcmEncodingMapper.resolveDecoderOutput(
                inputIsPcm = MimeTypes.AUDIO_RAW == format.sampleMimeType,
                inputEncoding = format.pcmEncoding,
                decoderOutputEncoding = decoderOutputEncoding
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Spectrum capture does not support the decoder PCM encoding", e)
            null
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

    /**
     * 捕获有效且尚未处理的输出缓冲区。
     *
     * Media3 可能对同一缓冲区重复调用处理方法，因此使用索引和时间戳共同去重。
     */
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
        val outputEncoding = mOutputEncoding ?: return
        if (sampleRate <= 0 || channelCount <= 0) return

        val input = buffer.asReadOnlyBuffer()
        analyzer.appendTimestampedPcm(
            inputBuffer = input,
            startTimeUs = bufferPresentationTimeUs,
            sampleRate = sampleRate,
            channelCount = channelCount,
            encoding = outputEncoding
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
        private const val TAG = "TimestampedPcmRenderer"
        private const val FORMAT_VALUE_UNSET = -1
    }
}
