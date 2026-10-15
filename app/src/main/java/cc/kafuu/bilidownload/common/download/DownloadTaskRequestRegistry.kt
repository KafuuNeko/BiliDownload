package cc.kafuu.bilidownload.common.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 维护服务请求发出到任务实际结束之间的在途身份。
 *
 * - 同一任务只能保留一个请求，覆盖 Android 尚未交付 Intent 的等待窗口。
 * - 请求令牌区分同一任务的多次恢复，旧请求清理不能释放新请求。
 * - 这里只保存进程内身份；进程重建后仍从原 Room 状态恢复，不修改失败记录。
 */
internal class DownloadTaskRequestRegistry {
    /** 一次服务请求的稳定任务身份和进程内令牌。 */
    data class Request(val taskId: Long, val token: Long)

    private val mRequests = mutableMapOf<Long, Request>()
    private var mNextToken = 0L
    private val mTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    val taskIds = mTaskIds.asStateFlow()

    /** 原子保留任务请求；已有在途请求时返回 null。 */
    @Synchronized
    fun reserve(taskId: Long): Request? {
        if (taskId in mRequests) return null
        val request = Request(taskId, ++mNextToken)
        mRequests[taskId] = request
        mTaskIds.value = mRequests.keys.toSet()
        return request
    }

    /** 判断任务是否处于提交、准备、执行或后处理阶段。 */
    @Synchronized
    fun contains(taskId: Long): Boolean = taskId in mRequests

    /** 获取当前令牌对应的请求，迟到的旧 Intent 不会取得新请求。 */
    @Synchronized
    fun find(taskId: Long, token: Long): Request? =
        mRequests[taskId]?.takeIf { it.token == token }

    /** 仅释放同一次请求；重复清理或旧请求清理返回 false。 */
    @Synchronized
    fun release(request: Request): Boolean {
        if (mRequests[request.taskId] != request) return false
        mRequests.remove(request.taskId)
        mTaskIds.value = mRequests.keys.toSet()
        return true
    }
}
