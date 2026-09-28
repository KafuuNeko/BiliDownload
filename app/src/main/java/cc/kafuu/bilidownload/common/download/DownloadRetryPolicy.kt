package cc.kafuu.bilidownload.common.download

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/** 同源重试、切源和刷新共用的恢复预算，服务端要求等待过久时保留手动恢复入口。 */
internal class DownloadRetryPolicy(
    val maxRetries: Int = 5,
    private val jitterMillis: () -> Long = { Random.nextLong(251) }
) {
    /** 返回本轮等待毫秒数；null 表示预算已用尽或错误不可恢复。 */
    fun delayMillis(error: ResourceDownloadException, retry: Int): Long? {
        if (!error.retryable || retry > maxRetries || error.retryAfterMillis > MAX_WAIT_MS) return null
        val backoff = (1000L shl (retry - 1).coerceIn(0, 4)) + jitterMillis()
        return maxOf(backoff, error.retryAfterMillis)
    }

    companion object {
        private const val MAX_WAIT_MS = 60_000L

        /** 解析 Retry-After 的秒数或 HTTP 日期；溢出值终止自动重试，避免提前违反限流。 */
        fun retryAfterMillis(value: String?, nowMillis: Long = System.currentTimeMillis()): Long {
            val text = value?.trim() ?: return 0L
            if (text.matches(Regex("[0-9]+"))) {
                val seconds = text.toLongOrNull() ?: return Long.MAX_VALUE
                return if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
            }
            val date = runCatching {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                    isLenient = false
                }.parse(text)
            }.getOrNull() ?: return 0L
            return (date.time - nowMillis).coerceAtLeast(0L)
        }
    }
}
