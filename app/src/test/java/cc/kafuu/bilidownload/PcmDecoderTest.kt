package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.audio.pcm.PcmDecoder
import cc.kafuu.bilidownload.common.audio.pcm.PcmSampleEncoding
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 验证各类 PCM 编码的归一化、声道混合和边界处理。 */
class PcmDecoderTest {
    /** 验证无符号 8 位单声道映射到 `[-1, 1]`。 */
    @Test
    fun decodes8BitMono() {
        val input = ByteBuffer.wrap(byteArrayOf(0, 128.toByte(), 255.toByte()))

        assertArrayEquals(
            floatArrayOf(-1f, 0f, 127f / 128f),
            PcmDecoder.decodeToMono(input, 1, PcmSampleEncoding.PCM_8_BIT),
            TOLERANCE
        )
    }

    /** 验证 16 位立体声逐帧平均为单声道。 */
    @Test
    fun decodes16BitStereoAndMixesChannels() {
        val input = nativeBuffer(8).apply {
            putShort(Short.MIN_VALUE)
            putShort(Short.MAX_VALUE)
            putShort(16_384)
            putShort(16_384)
            flip()
        }

        assertArrayEquals(
            floatArrayOf(-1f / 65_536f, 0.5f),
            PcmDecoder.decodeToMono(input, 2, PcmSampleEncoding.PCM_16_BIT),
            TOLERANCE
        )
    }

    /** 验证紧凑 24 位有符号整数及设备原生字节序。 */
    @Test
    fun decodes24BitPackedMono() {
        val input = nativeBuffer(9).apply {
            put24Bit(Int.MIN_VALUE shr 8)
            put24Bit(0)
            put24Bit(0x7f_ffff)
            flip()
        }

        assertArrayEquals(
            floatArrayOf(-1f, 0f, 0x7f_ffff / 8_388_608f),
            PcmDecoder.decodeToMono(input, 1, PcmSampleEncoding.PCM_24_BIT_PACKED),
            TOLERANCE
        )
    }

    /** 验证 32 位整数 PCM 的归一化结果。 */
    @Test
    fun decodes32BitMono() {
        val input = nativeBuffer(12).apply {
            putInt(Int.MIN_VALUE)
            putInt(0)
            putInt(Int.MAX_VALUE)
            flip()
        }

        assertArrayEquals(
            floatArrayOf(-1f, 0f, Int.MAX_VALUE / 2_147_483_648f),
            PcmDecoder.decodeToMono(input, 1, PcmSampleEncoding.PCM_32_BIT),
            TOLERANCE
        )
    }

    /** 验证浮点 PCM 会把越界值限制到有效振幅范围。 */
    @Test
    fun decodesFloatAndClampsOutOfRangeSamples() {
        val input = nativeBuffer(16).apply {
            putFloat(-2f)
            putFloat(-0.25f)
            putFloat(0.5f)
            putFloat(2f)
            flip()
        }

        assertArrayEquals(
            floatArrayOf(-1f, -0.25f, 0.5f, 1f),
            PcmDecoder.decodeToMono(input, 1, PcmSampleEncoding.PCM_FLOAT),
            TOLERANCE
        )
    }

    /** 验证末尾不足一个完整多声道帧的数据会被忽略。 */
    @Test
    fun ignoresIncompleteTrailingFrame() {
        val input = nativeBuffer(5).apply {
            putShort(Short.MAX_VALUE)
            putShort(Short.MIN_VALUE)
            put(0x7f)
            flip()
        }

        val output = PcmDecoder.decodeToMono(input, 2, PcmSampleEncoding.PCM_16_BIT)

        assertEquals(1, output.size)
    }

    private fun nativeBuffer(size: Int): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.nativeOrder())

    private fun ByteBuffer.put24Bit(value: Int) {
        if (order() == ByteOrder.LITTLE_ENDIAN) {
            put(value.toByte())
            put((value shr 8).toByte())
            put((value shr 16).toByte())
        } else {
            put((value shr 16).toByte())
            put((value shr 8).toByte())
            put(value.toByte())
        }
    }

    private companion object {
        private const val TOLERANCE = 0.000_001f
    }
}
