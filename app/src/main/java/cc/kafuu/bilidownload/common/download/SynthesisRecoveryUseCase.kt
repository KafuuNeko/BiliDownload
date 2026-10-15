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

/** 核验原音视频源文件后恢复合成，清理失败时保留源资源与可重试记录。 */
class SynthesisRecoveryUseCase(
    private val runSynthesis: suspend (DownloadTaskEntity) -> Unit,
    private val loadDash: suspend (DownloadTaskEntity) -> List<DownloadDashEntity> = DownloadRepository::queryDashList,
    private val loadResources: suspend (Long) -> List<DownloadResourceEntity> = DownloadRepository::queryResourcesForExport,
    private val clearMixedResources: suspend (Long) -> Boolean = DownloadRepository::deleteDownloadTaskMixedResource,
    private val saveTask: suspend (DownloadTaskEntity) -> Unit = DownloadRepository::update,
) {
    /** 已登记源文件可用且旧合成输出清理成功后，才推进到 SYNTHESIS。 */
    suspend fun execute(task: DownloadTaskEntity) {
        val dash = loadDash(task)
        val resources = loadResources(task.id)
        if (dash.none { it.type == DashType.VIDEO } || dash.none { it.type == DashType.AUDIO } ||
            !hasSource(resources, DownloadResourceType.VIDEO) ||
            !hasSource(resources, DownloadResourceType.AUDIO)) {
            throw IOException("Synthesis source is unavailable")
        }
        currentCoroutineContext().ensureActive()
        if (!clearMixedResources(task.id)) throw IOException("Unable to clear incomplete synthesis output")
        currentCoroutineContext().ensureActive()
        saveTask(task.apply { status = TaskStatus.SYNTHESIS.code })
        runSynthesis(task)
    }

    /** 与完成流程选择同一条源记录；非空工作文件才可以交给 FFmpeg。 */
    private fun hasSource(resources: List<DownloadResourceEntity>, type: Int): Boolean {
        val resource = resources.firstOrNull { it.type == type } ?: return false
        val file = File(resource.file)
        return file.isFile && file.length() > 0L
    }
}
