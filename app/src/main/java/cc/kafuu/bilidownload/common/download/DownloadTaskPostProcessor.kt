package cc.kafuu.bilidownload.common.download

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 串行执行完整的资源后处理，挂起等待数据库时仍保留文件及输出名称的独占权。
 *
 * 服务的下载完成、合成恢复和发布恢复共用一个实例；阻塞媒体及文件操作在 IO 线程执行。
 * 失败或取消会释放互斥锁，等待中的其他任务不受影响。
 */
internal class DownloadTaskPostProcessor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mMutex = Mutex()

    /** 从资源选择、登记到合成、发布和失败清理结束，始终独占后处理入口。 */
    suspend fun <T> execute(process: suspend () -> T): T = mMutex.withLock {
        withContext(dispatcher) { process() }
    }
}
