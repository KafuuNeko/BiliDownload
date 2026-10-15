package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.service.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 按固定任务 ID 快照提交失败重试，单项异常不阻断其他任务，不创建新下载记录。 */
class BatchRetryUseCase(
    private val loadTask: suspend (Long) -> DownloadTaskEntity? = DownloadRepository::getDownloadTaskByTaskId,
    private val isInFlight: (Long) -> Boolean = DownloadService::isTaskInFlight,
    private val submitRetry: (Long) -> Boolean = {
        DownloadService.retryTask(CommonLibs.requireContext(), it)
    },
) {
    /** 请求发出数不代表后台接受数或下载成功数。 */
    data class Result(val submittedCount: Int, val skippedCount: Int, val failedCount: Int)

    /** 本批次已处理的唯一任务数与总数。 */
    data class Progress(val processedCount: Int, val total: Int)

    /**
     * 去重后逐项查询实时状态；取消只停止后续提交，已发出的请求继续由服务管理。
     * [onProgress] 在 IO 调用协程内执行，页面更新应切回主线程。
     */
    suspend fun execute(
        taskIds: List<Long>,
        onProgress: suspend (Progress) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val targets = taskIds.distinct()
        var submitted = 0
        var skipped = 0
        var failed = 0
        onProgress(Progress(0, targets.size))

        // 每次提交前重新核对记录，列表变化不能使已完成或删除的任务再次执行。
        targets.forEachIndexed { index, taskId ->
            currentCoroutineContext().ensureActive()
            try {
                val task = loadTask(taskId)
                currentCoroutineContext().ensureActive()
                if (task == null || !DownloadTaskRecoveryUseCase.isFailed(task.status) || isInFlight(taskId)) {
                    skipped++
                } else if (submitRetry(taskId)) {
                    submitted++
                } else {
                    skipped++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed++
            }
            onProgress(Progress(index + 1, targets.size))
        }
        Result(submitted, skipped, failed)
    }
}
