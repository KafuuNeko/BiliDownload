package cc.kafuu.bilidownload.service

import cc.kafuu.bilidownload.common.download.DownloadTaskRequestRegistry
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import kotlinx.coroutines.sync.Mutex

/**
 * 记录当前 [DownloadService] 实例正在处理的唯一下载任务。
 *
 * - 重复启动刷新最近一次 startId，不重复登记相同任务。
 * - 接管请求与释放执行归属使用同一把锁，旧归属清理完成后才开放新的请求身份。
 * - 最后一个任务完成时返回最近的 startId，供 Service 安全判断是否可以停止自身。
 */
internal class DownloadServiceTaskRegistry(
    private val requests: DownloadTaskRequestRegistry,
) {
    /** 单次请求的执行归属；事件互斥与实体身份共同隔离迟到回调。 */
    class Assignment(val request: DownloadTaskRequestRegistry.Request) {
        @Volatile var task: DownloadTaskEntity? = null
        @Volatile var handedToDownloader = false
        val eventMutex = Mutex()
    }

    private val mAssignments = mutableMapOf<Long, Assignment>()
    private var mLatestStartId = 0

    /** 接管当前令牌；无内存请求时允许重建，已有新令牌时拒绝旧 Intent。 */
    @Synchronized
    fun tryRegister(taskId: Long, token: Long, startId: Int): Assignment? {
        mLatestStartId = maxOf(mLatestStartId, startId)
        if (taskId in mAssignments) return null
        val request = requests.find(taskId, token) ?: requests.reserve(taskId) ?: return null
        return Assignment(request).also { mAssignments[taskId] = it }
    }

    /** 返回事件对应的执行归属；消费时仍需通过 [owns] 再次核对。 */
    @Synchronized
    fun find(taskId: Long): Assignment? = mAssignments[taskId]

    /** 取得稳定快照，供服务销毁时释放已交给独立执行器的任务。 */
    @Synchronized
    fun snapshot(): List<Assignment> = mAssignments.values.toList()

    /** 旧请求或旧实体的事件不能更新同一任务的新一轮恢复。 */
    @Synchronized
    fun owns(assignment: Assignment, task: DownloadTaskEntity): Boolean =
        mAssignments[task.id] === assignment && assignment.task === task

    /**
     * 仅清理当前归属，全部服务登记清理后才发布请求身份已释放的状态。
     *
     * 新 Intent 的接管与此方法互斥；重复清理和旧请求清理不影响新任务。
     */
    @Synchronized
    fun finish(assignment: Assignment): Int? {
        val taskId = assignment.request.taskId
        if (mAssignments[taskId] !== assignment) return null
        mAssignments.remove(taskId)
        requests.release(assignment.request)
        return stopIdIfIdle()
    }

    /** 无有效任务的 Intent 也须参与 startId 判断，避免空闲服务一直驻留。 */
    @Synchronized
    fun stopIdIfIdle(): Int? = mLatestStartId.takeIf { mAssignments.isEmpty() }
}
