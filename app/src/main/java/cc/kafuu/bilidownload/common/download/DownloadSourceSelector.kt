package cc.kafuu.bilidownload.common.download

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 对候选源进行有流量、时间上限的小样本探测，保留未选中的地址供下载失败时回退。 */
internal class DownloadSourceSelector(
    private val client: OkHttpClient,
    private val buildRequest: (String) -> Request,
    private val timeoutMillis: Long = 3000L
) {
    /** 可用源按样本吞吐排序；[fallback] 位于可用源之后、探测失败的源之前。 */
    suspend fun rank(urls: List<String>, fallback: List<String> = emptyList()): List<String> =
        coroutineScope {
            val results = urls.distinct().map { url -> async { probe(url) } }.awaitAll()
            val available = results.filter { it.bytes > 0 }
                .sortedByDescending { it.bytes.toDouble() / it.elapsedNanos }
                .map { it.url }
            (available + fallback + urls).distinct()
        }

    /** 正文读取失败或为空时整次探测无效，取消直接向上传播。 */
    private suspend fun probe(url: String): Sample {
        val request = buildRequest(url).newBuilder()
            .header("Range", "bytes=0-${SAMPLE_BYTES - 1}")
            .header("Accept-Encoding", "identity")
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        val start = System.nanoTime()
        val bytes = try {
            call.consumeResponse { response ->
                if (response.code() != 200 && response.code() != 206) return@consumeResponse 0
                val body = response.body() ?: return@consumeResponse 0
                val expected = body.contentLength().takeIf { it >= 0 }?.coerceAtMost(SAMPLE_BYTES.toLong())
                val input = body.byteStream()
                val buffer = ByteArray(8192)
                var count = 0
                // 即使服务端忽略 Range 返回全量，也最多消费一个样本后关闭响应。
                while (count < SAMPLE_BYTES) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer, 0, minOf(buffer.size, SAMPLE_BYTES - count))
                    if (read < 0) break
                    count += read
                }
                if (expected != null && count.toLong() != expected) 0 else count
            }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            0
        }
        return Sample(url, bytes, (System.nanoTime() - start).coerceAtLeast(1L))
    }

    private data class Sample(val url: String, val bytes: Int, val elapsedNanos: Long)

    companion object {
        private const val SAMPLE_BYTES = 64 * 1024
    }
}
