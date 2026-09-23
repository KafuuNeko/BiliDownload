package cc.kafuu.bilidownload.common.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** 为卡片生成紧凑计数，向下保留一位小数，避免临界值提前进位到下一个单位。 */
object VideoCountFormatter {
    /** 中文使用万/亿，其他语言使用 K/M/B；未知或负数返回空串，由视图隐藏该项。 */
    fun format(count: Long?, locale: Locale = Locale.getDefault()): String {
        if (count == null || count < 0) return ""
        val units = if (locale.language == Locale.CHINESE.language) {
            listOf(100_000_000L to "亿", 10_000L to "万")
        } else {
            listOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
        }
        val (divisor, suffix) = units.firstOrNull { count >= it.first }
            ?: return count.toString()
        return BigDecimal.valueOf(count)
            .divide(BigDecimal.valueOf(divisor), 1, RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString() + suffix
    }
}
