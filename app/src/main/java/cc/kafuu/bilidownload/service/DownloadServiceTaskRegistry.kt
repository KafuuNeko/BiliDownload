package cc.kafuu.bilidownload.service

/**
 * 记录当前 [DownloadService] 实例正在处理的唯一下载任务。
 *
 * 重复启动仍会刷新最近一次 startId，但不会重复登记同一个任务。最后一个任务完成时返回
 * 最近的 startId，供 Service 安全地判断是否可以停止自身。
 */
internal class DownloadServiceTaskRegistry {
    private val mTaskIdSet = mutableSetOf<Long>()
    private var mLatestStartId = 0

    @Synchronized
    fun tryRegister(taskId: Long, startId: Int): Boolean {
        mLatestStartId = maxOf(mLatestStartId, startId)
        return mTaskIdSet.add(taskId)
    }

    @Synchronized
    fun finish(taskId: Long): Int? {
        if (!mTaskIdSet.remove(taskId) || mTaskIdSet.isNotEmpty()) return null
        return mLatestStartId
    }
}
