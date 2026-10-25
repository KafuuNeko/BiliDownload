package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/**
 * 恢复合成阶段的任务，区分已经提交的完整成品和需要重新合成的未完成输出。
 *
 * 成品尺寸落库、按设置删除源文件和发布完成属于不同检查点，进程可能在任意间隙结束。
 * 因此必须先查成品，再决定是否需要源文件，不能先清理唯一可用的合成输出。
 *
 * @param runSynthesis 需要重新合成时执行，沿用原任务身份与已登记的源资源。
 * @param finishCompletedSynthesis 复用成品时继续删源、转封装及发布，由回调负责后续持久化状态。
 * @param clearMixedResources 清理未完成合成输出；失败时禁止继续覆盖资源。
 * @param saveTask 重新合成前持久化 SYNTHESIS 状态，收到的是原任务对象。
 */
class SynthesisRecoveryUseCase(
    private val runSynthesis: suspend (DownloadTaskEntity) -> Unit,
    private val finishCompletedSynthesis: suspend (DownloadTaskEntity) -> Unit,
    private val loadDash: suspend (DownloadTaskEntity) -> List<DownloadDashEntity> = DownloadRepository::queryDashList,
    private val loadResources: suspend (Long) -> List<DownloadResourceEntity> = DownloadRepository::queryResourcesForExport,
    private val clearMixedResources: suspend (Long) -> Boolean = DownloadRepository::deleteDownloadTaskMixedResource,
    private val saveTask: suspend (DownloadTaskEntity) -> Unit = DownloadRepository::update,
) {
    /**
     * 按持久化检查点恢复原任务，协程取消与依赖回调的异常继续向上传播。
     *
     * 已提交且尺寸匹配的成品直接进入后处理，不要求已经按设置删除的源文件重新出现。
     * 只有确认没有可复用成品时才核验源文件、清理未完成输出并重新提交合成阶段。
     *
     * @throws IOException 重新合成所需音视频源不可用，或未完成输出无法安全清理时抛出。
     */
    suspend fun execute(task: DownloadTaskEntity) {
        val resources = loadResources(task.id)
        currentCoroutineContext().ensureActive()
        // 合成成功先保存非零尺寸，再按偏好删源；两步之后进程仍可能停在 SYNTHESIS。
        if (resources.any(::isCompletedMixedResource)) {
            finishCompletedSynthesis(task)
            return
        }

        // 预登记但没有提交尺寸的输出可能是半成品，必须保留原先的源校验和重新合成路径。
        val dash = loadDash(task)
        val hasRequiredDash =
            dash.any { it.type == DashType.VIDEO } && dash.any { it.type == DashType.AUDIO }
        if (!hasRequiredDash ||
            !hasSource(resources = resources, type = DownloadResourceType.VIDEO) ||
            !hasSource(resources = resources, type = DownloadResourceType.AUDIO)
        ) {
            throw IOException("Synthesis source is unavailable")
        }
        currentCoroutineContext().ensureActive()
        // 先确保源可用再删除旧输出；清理成功且未被取消后才持久化阶段并启动原生处理。
        if (!clearMixedResources(task.id)) throw IOException("Unable to clear incomplete synthesis output")
        currentCoroutineContext().ensureActive()
        saveTask(task.apply { status = TaskStatus.SYNTHESIS.code })
        runSynthesis(task)
    }

    /**
     * 用成功后提交的非零尺寸识别可复用成品，排除尺寸为零的预登记记录和已被截断的输出。
     *
     * 这是对已有成功检查点的核验，不是对任意同尺寸文件重新做媒体解码或内容摘要校验。
     */
    private fun isCompletedMixedResource(resource: DownloadResourceEntity): Boolean {
        if (resource.type != DownloadResourceType.MIXED || resource.storageSizeBytes <= 0L) return false
        val file = File(resource.file)
        return file.isFile && file.length() == resource.storageSizeBytes
    }

    /** 与完成流程选择同一条源记录；非空工作文件才可以交给 FFmpeg。 */
    private fun hasSource(resources: List<DownloadResourceEntity>, type: Int): Boolean {
        val resource = resources.firstOrNull { it.type == type } ?: return false
        val file = File(resource.file)
        return file.isFile && file.length() > 0L
    }
}
