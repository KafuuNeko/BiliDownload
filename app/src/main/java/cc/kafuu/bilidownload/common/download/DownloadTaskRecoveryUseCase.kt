package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 按数据库实时状态选择原任务的恢复阶段，不创建新记录或重置下载规格。
 *
 * 调用方先登记唯一请求，持有登记直到下载执行器或后处理真正结束。
 * [download] 返回 true 时执行器接管终态；其他结果由调用方立即释放登记。
 */
class DownloadTaskRecoveryUseCase(
    private val download: suspend (DownloadTaskEntity) -> Boolean,
    private val synthesize: suspend (DownloadTaskEntity) -> Unit,
    private val publish: suspend (DownloadTaskEntity) -> Unit,
    private val onFailure: suspend (DownloadTaskEntity, Phase) -> Unit,
    private val loadTask: suspend (Long) -> DownloadTaskEntity? = DownloadRepository::getDownloadTaskByTaskId,
    private val isDownloading: (DownloadTaskEntity) -> Boolean,
) {
    /** 恢复阶段及该阶段失败时的持久化状态。 */
    enum class Phase(val failureStatus: TaskStatus) {
        DOWNLOAD(TaskStatus.DOWNLOAD_FAILED),
        SYNTHESIS(TaskStatus.SYNTHESIS_FAILED),
        PUBLISH(TaskStatus.PUBLISH_FAILED),
    }

    /** 区分执行器接管、后处理结束和请求已失效，供服务判断登记释放时机。 */
    enum class Result { HANDED_TO_DOWNLOADER, FINISHED, SKIPPED }

    companion object {
        /** 当前失败页与手动重试共同支持的状态，不改变既有数据库编码。 */
        val failedStatuses = listOf(
            TaskStatus.DOWNLOAD_FAILED,
            TaskStatus.SYNTHESIS_FAILED,
            TaskStatus.PUBLISH_FAILED,
        )

        /** 判断持久化状态是否允许用户发起失败重试。 */
        fun isFailed(status: Int): Boolean = failedStatuses.any { it.code == status }

        /** 已完成或未知状态不进入恢复；中断的活动状态按原阶段继续。 */
        fun phaseFor(status: Int): Phase? = when (status) {
            TaskStatus.PREPARE.code, TaskStatus.DOWNLOADING.code,
            TaskStatus.DOWNLOAD_FAILED.code -> Phase.DOWNLOAD
            TaskStatus.SYNTHESIS.code, TaskStatus.SYNTHESIS_FAILED.code -> Phase.SYNTHESIS
            TaskStatus.PUBLISHING.code, TaskStatus.PUBLISH_FAILED.code -> Phase.PUBLISH
            else -> null
        }
    }

    /**
     * 重新读取任务后恢复；[retryOnly] 为 true 时拒绝已完成或正在处理的旧列表目标。
     *
     * 单项业务异常交给 [onFailure] 保存对应失败阶段，取消始终向上传播。
     */
    suspend fun execute(taskId: Long, retryOnly: Boolean): Result {
        val task = loadTask(taskId) ?: return Result.SKIPPED
        currentCoroutineContext().ensureActive()
        if ((retryOnly && !isFailed(task.status)) || isDownloading(task)) return Result.SKIPPED
        val phase = phaseFor(task.status) ?: return Result.SKIPPED

        // 直接进入原失败阶段，合成与发布不依赖重新取得播放流。
        return try {
            when (phase) {
                Phase.DOWNLOAD -> if (download(task)) Result.HANDED_TO_DOWNLOADER else Result.SKIPPED
                Phase.SYNTHESIS -> { synthesize(task); Result.FINISHED }
                Phase.PUBLISH -> { publish(task); Result.FINISHED }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            // 合成完成后发布可能失败，使用已推进的阶段而不是最初的阶段。
            onFailure(task, phaseFor(task.status) ?: phase)
            Result.FINISHED
        }
    }
}
