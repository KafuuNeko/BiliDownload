package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.download.DownloadCheckpoint.Companion.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 下载一个不可变媒体资源，负责有界重试、候选源轮换、检查点及完整文件交付。
 *
 * - 调用方提供同一 dashId/codecId 的候选地址，最多刷新一次，所有恢复共用重试预算。
 * - 暂停和取消通过调用协程传播；重试等待及响应体读取均可取消。
 * - 本地写入错误直接失败，未完成缓存保留给后续恢复，成品仅在完整校验后可见。
 */
internal class ResourceDownloader(
    private val client: OkHttpClient,
    private val buildRequest: (String) -> Request,
    private val retryPolicy: DownloadRetryPolicy = DownloadRetryPolicy(),
    private val waitBeforeRetry: suspend (Long) -> Unit = { delay(it) }
) {
    /**
     * 完成资源下载；[onProgress] 的两个参数分别为已下载字节数和总字节数（未知为 -1）。
     * [onRetry] 只在恢复阶段传入非 null，不能据此将数据库任务提前置为失败。
     */
    suspend fun download(
        urls: List<String>,
        cache: File,
        output: File,
        refreshUrls: suspend () -> List<String>,
        onProgress: (Long, Long) -> Unit,
        onRetry: (DownloadRetry?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val sources = Sources(urls)
        val checkpoint = DownloadCheckpoint(cache)
        var retry = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                // 地址刷新也消耗一次尝试，避免刷新失败与媒体重试形成嵌套无限循环。
                if (sources.needsRefresh) {
                    sources.beginRefresh()
                    sources.replace(refreshUrls())
                }
                onRetry(null)
                transfer(sources.current(), cache, checkpoint, onProgress)
                currentCoroutineContext().ensureActive()
                publish(cache, output)
                onProgress(output.length(), output.length())
                break
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                val failure = classify(error)
                val waitMillis = retryPolicy.delayMillis(failure, ++retry) ?: throw failure
                onRetry(DownloadRetry(retry, retryPolicy.maxRetries, failure.failure))
                sources.failed(failure)
                waitBeforeRetry(waitMillis)
            }
        }
    }

    /** 校验缓存身份后请求资源；换源缺少强校验标识时从头下载，绝不盲目跨源拼接。 */
    private suspend fun transfer(
        url: String,
        cache: File,
        checkpoint: DownloadCheckpoint,
        onProgress: (Long, Long) -> Unit
    ) {
        val key = DownloadCheckpoint.sourceKey(url)
        var identity = checkpoint.read()
        if (cache.length() > 0 && (identity == null ||
                (identity.sourceKey != key && identity.etag == null))) {
            checkpoint.reset()
            identity = null
        }
        val offset = cache.length()
        val request = buildRequest(url).newBuilder().header("Accept-Encoding", "identity").apply {
            if (offset > 0) {
                header("Range", "bytes=$offset-")
                identity?.etag?.let { header("If-Range", it) }
            }
        }.build()
        // 网络调用和正文读取共享取消监听；文件 IO 在独立的 storage 边界内分类。
        client.newCall(request).consumeResponse { response ->
            val plan = responsePlan(response, offset, identity, checkpoint)
            if (!plan.append) checkpoint.reset()
            checkpoint.write(DownloadCheckpoint.Identity(key, plan.etag, plan.total))
            writeBody(response, cache, plan, onProgress)
        }
    }

    /** 验证范围、编码和资源版本；错误响应不能污染已经落盘的前缀。 */
    private fun responsePlan(
        response: Response,
        offset: Long,
        previous: DownloadCheckpoint.Identity?,
        checkpoint: DownloadCheckpoint
    ): ResponsePlan {
        if (response.code() == 416 && offset > 0) {
            checkpoint.reset()
            throw invalidResponse()
        }
        if (response.code() != 200 && response.code() != 206) {
            throw ResourceDownloadException(
                DownloadFailure(DownloadFailure.Kind.HTTP, response.code()),
                response.code() in RETRYABLE_HTTP_CODES,
                DownloadRetryPolicy.retryAfterMillis(response.header("Retry-After"))
            )
        }
        val body = response.body() ?: throw invalidResponse()
        val encoding = response.header("Content-Encoding")
        if (encoding != null && !encoding.equals("identity", true)) throw invalidResponse()
        val etag = DownloadCheckpoint.strongEtag(response.header("ETag"))
        if (response.code() == 200) return ResponsePlan(false, 0, body.contentLength(), etag)

        // 206 必须明确对应请求偏移及总长度，不能仅依据状态码追加。
        val range = CONTENT_RANGE.matchEntire(response.header("Content-Range").orEmpty())
        val start = range?.groupValues?.get(1)?.toLongOrNull()
        val end = range?.groupValues?.get(2)?.toLongOrNull()
        val total = range?.groupValues?.get(3)?.toLongOrNull()
        if (start != offset || end == null || total == null || end < offset || end >= total ||
            (body.contentLength() >= 0 && body.contentLength() != end - offset + 1)) {
            checkpoint.reset()
            throw invalidResponse()
        }
        if (offset > 0 && previous != null &&
            ((previous.total >= 0 && previous.total != total) ||
                (previous.etag != null && previous.etag != etag))) {
            checkpoint.reset()
            throw invalidResponse()
        }
        return ResponsePlan(offset > 0, offset, total, etag)
    }

    /** 流式写入并检查总长度；读取异常保留前缀，磁盘错误不进入网络恢复。 */
    private suspend fun writeBody(
        response: Response,
        cache: File,
        plan: ResponsePlan,
        onProgress: (Long, Long) -> Unit
    ) {
        val file = storage { RandomAccessFile(cache, "rw") }
        try {
            storage {
                if (!plan.append) file.setLength(0)
                file.seek(plan.offset)
            }
            var downloaded = plan.offset
            onProgress(downloaded, plan.total)
            val input = response.body()?.byteStream() ?: throw invalidResponse()
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                storage { file.write(buffer, 0, count) }
                downloaded += count
                onProgress(downloaded, plan.total)
            }
            // 未知长度只能以正常 EOF 为边界；空文件永远不能作为可用媒体交付。
            if (downloaded == 0L || (plan.total >= 0 && downloaded != plan.total)) {
                throw invalidResponse()
            }
        } finally {
            storage { file.close() }
        }
    }

    /** 使用同目录暂存文件完成跨文件系统复制，失败时保留缓存且不暴露半截成品。 */
    private suspend fun publish(cache: File, output: File) {
        storage {
            output.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException() }
        }
        if (storage { cache.renameTo(output) }) return
        val staging = File(output.path + ".downloading")
        try {
            storage {
                cache.inputStream().use { input ->
                    staging.outputStream().use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                        }
                    }
                }
                if (!staging.renameTo(output)) throw IOException()
                cache.delete()
            }
        } finally {
            staging.delete()
        }
    }

    private fun classify(error: IOException): ResourceDownloadException =
        error as? ResourceDownloadException ?: ResourceDownloadException(
            DownloadFailure(DownloadFailure.Kind.NETWORK),
            error !is SSLHandshakeException && error !is SSLPeerUnverifiedException
        )

    private fun invalidResponse() = ResourceDownloadException(
        DownloadFailure(DownloadFailure.Kind.INVALID_RESPONSE), retryable = true
    )

    private data class ResponsePlan(val append: Boolean, val offset: Long, val total: Long, val etag: String?)

    /** 单次资源调用的候选游标，HTTP 错误直接换源，网络中断先在原源续传一次。 */
    private class Sources(urls: List<String>) {
        private var candidates = urls.distinct()
        private var index = 0
        private var failures = 0
        private var refreshed = false
        var needsRefresh = false
            private set

        /** 空候选集合是明确的资源不可用，不能在恢复循环内空转。 */
        fun current(): String = candidates.getOrNull(index) ?: throw ResourceDownloadException(
            DownloadFailure(DownloadFailure.Kind.SOURCE_UNAVAILABLE)
        )

        /** 原源恢复失败后推进候选，完整轮换结束时最多安排一次刷新。 */
        fun failed(error: ResourceDownloadException) {
            if (++failures < 2 && error.failure.kind in setOf(
                    DownloadFailure.Kind.NETWORK, DownloadFailure.Kind.INVALID_RESPONSE
                )) return
            failures = 0
            index++
            if (index < candidates.size) return
            index = 0
            needsRefresh = !refreshed
            // 刷新失败也不得重新安排第二次刷新。
            refreshed = true
        }

        /** 调用外部刷新前消费标记，即使刷新抛出异常也不得再次请求刷新。 */
        fun beginRefresh() {
            needsRefresh = false
        }

        /** 接受已完成身份匹配的新候选，继续共享原恢复预算。 */
        fun replace(urls: List<String>) {
            needsRefresh = false
            candidates = urls.distinct()
            index = 0
        }
    }

    companion object {
        private const val BUFFER_SIZE = 128 * 1024
        private val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)")
        private val RETRYABLE_HTTP_CODES = setOf(403, 404, 408, 429, 500, 502, 503, 504)
    }
}
