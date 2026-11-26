package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.network.model.BiliDanmakuProtoParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

/** 验证 Protobuf 毫秒进度进入秒制模型后，CSV 使用的时间轴保持正确。 */
class BiliDanmakuProtoParserTest {
    /** 覆盖 issue #72 的 8888 毫秒被导出为 02:28:08.000 的场景。 */
    @Test
    fun protobufMillisecondsUseSecondsInCsvTime() {
        val data = createProtoReply(progressMillis = 8888)
        val danmaku = BiliDanmakuProtoParser.parseFromProto(data = data).single()

        assertEquals(8.888f, danmaku.progress, 0.000001f)
        assertEquals("00:00:08.888", danmaku.getFormattedTime())
        assertEquals("test", danmaku.content)
    }

    /** 验证零值、亚秒精度以及分钟和小时进位，防止先整除而丢失毫秒。 */
    @Test
    fun protobufMillisecondsPreserveZeroSubsecondsAndTimeBoundaries() {
        // 显式列出秒值与 CSV 时间，避免用待测换算逻辑计算期望结果。
        val examples = mapOf(
            0 to (0f to "00:00:00.000"),
            125 to (0.125f to "00:00:00.125"),
            1234 to (1.234f to "00:00:01.234"),
            23_456 to (23.456f to "00:00:23.456"),
            59_500 to (59.5f to "00:00:59.500"),
            59_999 to (59.999f to "00:00:59.999"),
            60_000 to (60f to "00:01:00.000"),
            3_600_000 to (3600f to "01:00:00.000"),
            3_600_125 to (3600.125f to "01:00:00.125"),
        )
        // 每个样本都经过生产解析器和 CSV 共用的时间格式化方法。
        examples.forEach { (progressMillis, expected) ->
            val (expectedSeconds, expectedTime) = expected
            val data = createProtoReply(progressMillis = progressMillis)
            val danmaku = BiliDanmakuProtoParser.parseFromProto(data = data).single()

            assertEquals(expectedSeconds, danmaku.progress, 0.000001f)
            assertEquals(expectedTime, danmaku.getFormattedTime())
        }
    }

    /** 验证秒制模型的浮点误差与进位，覆盖 XML 和 Protobuf 共用的 CSV 时间格式化。 */
    @Test
    fun csvTimeRoundsSecondsBeforeSplittingMinuteAndHour() {
        val data = createProtoReply(progressMillis = 0)
        val danmaku = BiliDanmakuProtoParser.parseFromProto(data = data).single()
        val examples = mapOf(
            8.888f to "00:00:08.888",
            59.9996f to "00:01:00.000",
            3599.9996f to "01:00:00.000",
        )
        examples.forEach { (progressSeconds, expectedTime) ->
            assertEquals(expectedTime, danmaku.copy(progress = progressSeconds).getFormattedTime())
        }
    }

    /** 验证一个回复中每条弹幕独立转换单位，并保留原始顺序。 */
    @Test
    fun allElementsAreNormalizedIndependently() {
        val data = createProtoReply(progressMillis = 8888) +
            createProtoReply(progressMillis = 125) +
            createProtoReply(progressMillis = 60_000)
        val danmakuList = BiliDanmakuProtoParser.parseFromProto(data = data)

        assertEquals(listOf(8.888f, 0.125f, 60f), danmakuList.map { it.progress })
        assertEquals(
            listOf("00:00:08.888", "00:00:00.125", "00:01:00.000"),
            danmakuList.map { it.getFormattedTime() },
        )
    }

    /** 构造只改变出现时间的完整弹幕元素，使用真实 Protobuf 线格式经过生产解析器。 */
    private fun createProtoReply(progressMillis: Int): ByteArray {
        // 固定 id=1、mode=1、fontsize=25、color=0、mid_hash="u"、content="test"、ctime=1。
        val element = byteArrayOf(0x08, 0x01, 0x10) + encodeVarint(value = progressMillis) +
            byteArrayOf(
                0x18, 0x01, 0x20, 0x19, 0x28, 0x00, 0x32, 0x01, 0x75,
                0x3A, 0x04, 0x74, 0x65, 0x73, 0x74, 0x40, 0x01,
            )
        // 外层 field 1 是重复的 DanmakuElem，当前固定元素长度小于 128 字节。
        return byteArrayOf(0x0A, element.size.toByte()) + element
    }

    /** 为测试回复编码非负的毫秒值。 */
    private fun encodeVarint(value: Int): ByteArray {
        var remaining = value
        val output = ByteArrayOutputStream()
        while (remaining > 0x7F) {
            output.write((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        output.write(remaining)
        return output.toByteArray()
    }
}
