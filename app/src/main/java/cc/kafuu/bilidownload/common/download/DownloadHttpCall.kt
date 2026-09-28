package cc.kafuu.bilidownload.common.download

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response

/**
 * 在 IO 线程消费完整响应生命周期，父任务取消时立即关闭正在连接或读取正文的 Call。
 *
 * 取消监听持续到正文消费结束，避免仅在收到响应头前可取消；响应和监听均由本方法释放。
 */
internal suspend fun <T> Call.consumeResponse(block: suspend (Response) -> T): T =
    withContext(Dispatchers.IO) {
        coroutineScope {
            val cancellation = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    this@consumeResponse.cancel()
                }
            }
            try {
                currentCoroutineContext().ensureActive()
                execute().use { block(it) }
            } finally {
                cancellation.cancel()
            }
        }
    }
