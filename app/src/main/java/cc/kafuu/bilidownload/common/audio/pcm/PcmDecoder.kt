package cc.kafuu.bilidownload.common.audio.pcm

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PCM 样本的存储格式及单声道样本占用的字节数。 */
enum class PcmSampleEncoding(val bytesPerSample: Int) {
    PCM_8_BIT(1),
    PCM_16_BIT(2),
    PCM_24_BIT_PACKED(3),
    PCM_32_BIT(4),
    PCM_FLOAT(4)
}

/**
 * 将不同位深的交错 PCM 数据统一解码为单声道浮点样本。
 *
 * 解码结果限制在 `[-1, 1]`，多声道按帧求平均；末尾不足一帧的数据会被忽略。
 * 所有读取都基于输入缓冲区的只读副本，不改变调用方的 position。
 */
object PcmDecoder {
    /**
     * 计算输入缓冲区中包含的完整 PCM 帧数。
     *
     * @throws IllegalArgumentException 当声道数不是正数时抛出。
     */
    fun frameCount(
        input: ByteBuffer,
        channelCount: Int,
        encoding: PcmSampleEncoding
    ): Int {
        require(channelCount > 0) { "Channel count must be positive." }
        return input.remaining() / (encoding.bytesPerSample * channelCount)
    }

    /** 解码全部完整帧，并为结果分配恰好大小的浮点数组。 */
    fun decodeToMono(
        input: ByteBuffer,
        channelCount: Int,
        encoding: PcmSampleEncoding
    ): FloatArray {
        return FloatArray(frameCount(input, channelCount, encoding)).also { output ->
            decodeToMono(input, channelCount, encoding, output)
        }
    }

    /**
     * 将全部完整帧解码到调用方提供的数组中。
     *
     * @return 实际写入的单声道样本数量。
     * @throws IllegalArgumentException 当输出数组容量不足时抛出。
     */
    fun decodeToMono(
        input: ByteBuffer,
        channelCount: Int,
        encoding: PcmSampleEncoding,
        output: FloatArray
    ): Int {
        val frames = frameCount(input, channelCount, encoding)
        require(output.size >= frames) { "Output buffer is too small for $frames PCM frames." }
        val buffer = input.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        var outputIndex = 0
        while (outputIndex < frames) {
            var mixed = 0f
            repeat(channelCount) {
                mixed += readSample(buffer, encoding)
            }
            output[outputIndex++] = (mixed / channelCount).coerceIn(-1f, 1f)
        }
        return outputIndex
    }

    private fun readSample(buffer: ByteBuffer, encoding: PcmSampleEncoding): Float {
        return when (encoding) {
            PcmSampleEncoding.PCM_8_BIT ->
                ((buffer.get().toInt() and 0xff) - 128) / 128f

            PcmSampleEncoding.PCM_16_BIT -> buffer.short / 32_768f
            PcmSampleEncoding.PCM_24_BIT_PACKED -> read24BitSample(buffer) / 8_388_608f
            PcmSampleEncoding.PCM_32_BIT -> buffer.int / 2_147_483_648f
            PcmSampleEncoding.PCM_FLOAT -> buffer.float.coerceIn(-1f, 1f)
        }
    }

    /** 按设备原生字节序读取带符号的紧凑 24 位整数。 */
    private fun read24BitSample(buffer: ByteBuffer): Int {
        val first = buffer.get().toInt() and 0xff
        val second = buffer.get().toInt() and 0xff
        val third = buffer.get().toInt() and 0xff
        val raw = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            first or (second shl 8) or (third shl 16)
        } else {
            third or (second shl 8) or (first shl 16)
        }
        return if (raw and 0x80_0000 != 0) raw or -0x100_0000 else raw
    }
}
