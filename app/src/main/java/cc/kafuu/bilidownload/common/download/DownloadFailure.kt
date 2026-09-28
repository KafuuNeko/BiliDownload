package cc.kafuu.bilidownload.common.download

import java.io.IOException

/** 可安全传递到页面和日志的失败信息，不包含请求地址、响应正文或账号凭据。 */
data class DownloadFailure(
    val kind: Kind,
    val httpCode: Int? = null
) {
    /** 区分网络恢复、响应校验、本地写入和流地址刷新失败。 */
    enum class Kind { NETWORK, HTTP, INVALID_RESPONSE, STORAGE, SOURCE_UNAVAILABLE, UNKNOWN }
}

/** 下载层内部错误；[retryable] 只控制有界恢复，不改变用户主动取消的语义。 */
internal class ResourceDownloadException(
    val failure: DownloadFailure,
    val retryable: Boolean = false,
    val retryAfterMillis: Long = 0L
) : IOException(failure.kind.name)

/** 当前资源正在等待的恢复次数；[attempt] 从 1 开始，不计首次请求。 */
data class DownloadRetry(val attempt: Int, val maxRetries: Int, val failure: DownloadFailure)
