package cc.kafuu.bilidownload

import androidx.media3.common.C
import cc.kafuu.bilidownload.common.audio.pcm.PcmEncodingMapper
import cc.kafuu.bilidownload.common.audio.pcm.PcmSampleEncoding
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证播放器解码输出的 PCM 编码解析及默认值。 */
class PcmEncodingMapperTest {
    /** 压缩音频解码器省略 PCM 编码时应采用 Media3 的 16 位默认值。 */
    @Test
    fun compressedInputWithoutDecoderEncodingDefaultsTo16BitPcm() {
        val encoding = PcmEncodingMapper.resolveDecoderOutput(
            inputIsPcm = false,
            inputEncoding = C.ENCODING_INVALID,
            decoderOutputEncoding = null
        )

        assertEquals(PcmSampleEncoding.PCM_16_BIT, encoding)
    }

    /** 原始 PCM 输入应保留 Media3 格式声明的编码。 */
    @Test
    fun rawInputUsesDeclaredMedia3Encoding() {
        val encoding = PcmEncodingMapper.resolveDecoderOutput(
            inputIsPcm = true,
            inputEncoding = C.ENCODING_PCM_FLOAT,
            decoderOutputEncoding = null
        )

        assertEquals(PcmSampleEncoding.PCM_FLOAT, encoding)
    }
}
