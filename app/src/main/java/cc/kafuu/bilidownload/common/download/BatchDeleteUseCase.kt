package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 批量取消下载并删除对应任务和资源的应用用例。 */
class BatchDeleteUseCase(
    private val cancelDownload: (Long) -> Unit = DownloadManager::cancelDownload,
    private val deleteTask: suspend (Long) -> Boolean = DownloadRepository::deleteDownloadTask,
) {
    data class Target(
        val taskId: Long,
        val groupId: Long?,
    )

    data class Result(val failedTaskIds: Set<Long>) {
        val hasFailure: Boolean
            get() = failedTaskIds.isNotEmpty()
    }

    suspend fun execute(targets: List<Target>): Result = withContext(Dispatchers.IO) {
        val failedTaskIds = buildSet {
            targets.forEach { target ->
                target.groupId?.let { groupId ->
                    runCatching { cancelDownload(groupId) }
                }
                val deleted = runCatching { deleteTask(target.taskId) }.getOrDefault(false)
                if (!deleted) add(target.taskId)
            }
        }
        Result(failedTaskIds)
    }
}
