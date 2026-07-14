package cc.kafuu.bilidownload.common.audio.pcm

import android.media.AudioFormat
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi

/**
 * 把 Android 平台和 Media3 各自的编码常量转换为统一的 PCM 样本格式。
 *
 * 两套常量的数值空间不同，必须通过对应入口转换，不能直接混用。
 */
object PcmEncodingMapper {
    /**
     * 解析播放器解码后的 PCM 编码。
     *
     * [inputIsPcm] 为 `true` 时，输入格式本身就是 PCM，应以 Media3 输入格式声明的编码为准；
     * 对 AAC、MP3 等压缩输入，则优先使用解码器输出格式声明的平台编码。部分解码器不会为默认
     * 16 位输出写入 `MediaFormat.KEY_PCM_ENCODING`，此时按照 Media3 的规则回退为 16 位 PCM。
     */
    fun resolveDecoderOutput(
        inputIsPcm: Boolean,
        inputEncoding: Int,
        decoderOutputEncoding: Int?
    ): PcmSampleEncoding {
        return when {
            inputIsPcm -> fromMedia3(inputEncoding)
            decoderOutputEncoding != null -> fromPlatform(decoderOutputEncoding)
            else -> PcmSampleEncoding.PCM_16_BIT
        }
    }

    /**
     * 转换 [AudioFormat] 使用的平台编码常量。
     *
     * 24 位和 32 位平台常量仅在 Android 12 及以上可用。
     * @throws IllegalArgumentException 当编码不受支持时抛出。
     */
    fun fromPlatform(encoding: Int): PcmSampleEncoding {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> PcmSampleEncoding.PCM_8_BIT
            AudioFormat.ENCODING_PCM_16BIT -> PcmSampleEncoding.PCM_16_BIT
            AudioFormat.ENCODING_PCM_FLOAT -> PcmSampleEncoding.PCM_FLOAT
            else -> fromApi31PlatformEncoding(encoding)
        }
    }

    /**
     * 转换 Media3 [C] 定义的 PCM 编码常量。
     *
     * @throws IllegalArgumentException 当编码不受支持时抛出。
     */
    @OptIn(UnstableApi::class)
    fun fromMedia3(encoding: Int): PcmSampleEncoding {
        return when (encoding) {
            C.ENCODING_PCM_8BIT -> PcmSampleEncoding.PCM_8_BIT
            C.ENCODING_PCM_16BIT -> PcmSampleEncoding.PCM_16_BIT
            C.ENCODING_PCM_24BIT -> PcmSampleEncoding.PCM_24_BIT_PACKED
            C.ENCODING_PCM_32BIT -> PcmSampleEncoding.PCM_32_BIT
            C.ENCODING_PCM_FLOAT -> PcmSampleEncoding.PCM_FLOAT
            else -> throw IllegalArgumentException("Unsupported Media3 PCM encoding: $encoding")
        }
    }

    private fun fromApi31PlatformEncoding(encoding: Int): PcmSampleEncoding {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (encoding) {
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> PcmSampleEncoding.PCM_24_BIT_PACKED
                AudioFormat.ENCODING_PCM_32BIT -> PcmSampleEncoding.PCM_32_BIT
                else -> throw IllegalArgumentException("Unsupported platform PCM encoding: $encoding")
            }
        }
        throw IllegalArgumentException("Unsupported platform PCM encoding: $encoding")
    }
}
