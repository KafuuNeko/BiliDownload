package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.HistoryDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.RVViewModel
import com.arialyy.aria.core.Aria
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HistoryViewModel : RVViewModel() {
    val centerCrop = CenterCrop()

    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskWithVideoDetails>>
        private set

    // 多选模式状态
    private val mMultiSelectModeLiveData = MutableLiveData(false)
    val multiSelectModeLiveData = mMultiSelectModeLiveData.liveData()

    // 已选中的任务ID集合
    private val mSelectedIdsLiveData = MutableLiveData<Set<Long>>(emptySet())
    val selectedIdsLiveData = mSelectedIdsLiveData.liveData()

    // 批量导出进度
    private val mBatchExportProgressLiveData = MutableLiveData<BatchExportProgress?>(null)
    val batchExportProgressLiveData = mBatchExportProgressLiveData.liveData()

    companion object {
        class RequestExportDirAction : ViewAction()
    }

    data class BatchExportProgress(
        val current: Int,
        val total: Int
    )

    fun initData(status: List<TaskStatus>) {
        latestDownloadTaskLiveData = DownloadRepository.queryDownloadTasksDetailsLiveData(status)
    }

    fun getStatusIcon(task: DownloadTaskWithVideoDetails) = CommonLibs.getDrawable(
        when (TaskStatus.entries.find { it.code == task.downloadTask.status }) {
            TaskStatus.PREPARE -> R.drawable.ic_prepare
            TaskStatus.DOWNLOADING -> R.drawable.ic_downloading
            TaskStatus.DOWNLOAD_FAILED -> R.drawable.ic_download_failed_cloud
            TaskStatus.SYNTHESIS -> R.drawable.ic_synthesis
            TaskStatus.SYNTHESIS_FAILED -> R.drawable.ic_synthesis_failed
            TaskStatus.COMPLETED -> R.drawable.ic_download_done_cloud
            else -> R.drawable.ic_unknown_med
        }
    )

    fun getStatusText(task: DownloadTaskWithVideoDetails): String {
        val percent = task.downloadTask.groupId?.let {
            Aria.download(this).loadGroup(it).percent
        }
        return "${percent ?: 0}%"
    }

    fun entryHistoryDetails(task: DownloadTaskWithVideoDetails) {
        if (mMultiSelectModeLiveData.value == true) {
            toggleItemSelection(task.downloadTask.id)
            return
        }
        startActivity(
            HistoryDetailsActivity::class.java,
            HistoryDetailsActivity.buildIntent(task.downloadTask.id)
        )
    }

    fun onItemLongClick(task: DownloadTaskWithVideoDetails): Boolean {
        if (mMultiSelectModeLiveData.value == true) return false
        enterMultiSelectMode()
        toggleItemSelection(task.downloadTask.id)
        return true
    }

    fun enterMultiSelectMode() {
        mMultiSelectModeLiveData.value = true
    }

    fun exitMultiSelectMode() {
        mMultiSelectModeLiveData.value = false
        mSelectedIdsLiveData.value = emptySet()
    }

    fun toggleItemSelection(taskId: Long) {
        val current = mSelectedIdsLiveData.value ?: emptySet()
        mSelectedIdsLiveData.value = if (current.contains(taskId)) {
            current - taskId
        } else {
            current + taskId
        }
        if (mSelectedIdsLiveData.value.isNullOrEmpty()) {
            exitMultiSelectMode()
        }
    }

    fun selectAll() {
        val allIds = latestDownloadTaskLiveData.value?.map { it.downloadTask.id }?.toSet()
        mSelectedIdsLiveData.value = allIds ?: emptySet()
    }

    fun isItemSelected(taskId: Long): Boolean {
        return mSelectedIdsLiveData.value?.contains(taskId) == true
    }

    fun getSelectedCount(): Int = mSelectedIdsLiveData.value?.size ?: 0

    suspend fun deleteSelectedTasks() {
        val selectedIds = mSelectedIdsLiveData.value ?: return
        withContext(Dispatchers.IO) {
            for (taskId in selectedIds) {
                DownloadRepository.deleteDownloadTask(taskId)
                latestDownloadTaskLiveData.value?.find { it.downloadTask.id == taskId }
                    ?.downloadTask?.groupId?.let { groupId ->
                        Aria.download(this@HistoryViewModel).loadGroup(groupId)
                            ?.ignoreCheckPermissions()?.cancel(true)
                    }
            }
        }
        exitMultiSelectMode()
    }

    fun tryBatchExport() {
        val selectedIds = mSelectedIdsLiveData.value
        if (selectedIds.isNullOrEmpty()) return
        sendViewAction(RequestExportDirAction())
    }

    suspend fun executeBatchExport(treeUri: Uri) {
        val context = CommonLibs.requireContext()
        val selectedIds = mSelectedIdsLiveData.value ?: return
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return

        val exportItems = withContext(Dispatchers.IO) {
            buildExportItems(selectedIds)
        }

        if (exportItems.isEmpty()) {
            popMessage(
                ToastMessageAction(CommonLibs.getString(R.string.batch_export_no_resource_message))
            )
            mBatchExportProgressLiveData.postValue(null)
            return
        }

        val total = exportItems.size
        mBatchExportProgressLiveData.postValue(BatchExportProgress(0, total))

        withContext(Dispatchers.IO) {
            var successCount = 0
            for ((index, item) in exportItems.withIndex()) {
                mBatchExportProgressLiveData.postValue(BatchExportProgress(index + 1, total))
                val safeName = resolveConflictName(treeDoc, item.fileName)
                val created = treeDoc.createFile(item.mimeType, safeName)
                if (created?.uri == null) continue
                val success = copyFileToUri(context, created.uri, item.sourceFile)
                if (success) successCount++
            }
            mBatchExportProgressLiveData.postValue(null)
            if (successCount > 0) {
                popMessage(
                    ToastMessageAction(
                        CommonLibs.getString(R.string.batch_export_success_message, successCount)
                    )
                )
            } else {
                popMessage(
                    ToastMessageAction(
                        CommonLibs.getString(
                            R.string.batch_export_failed_message,
                            CommonLibs.getString(R.string.error_unknown)
                        )
                    )
                )
            }
        }

        exitMultiSelectMode()
    }

    private data class ExportItem(
        val fileName: String,
        val mimeType: String,
        val sourceFile: File
    )

    private suspend fun buildExportItems(selectedIds: Set<Long>): List<ExportItem> {
        val items = mutableListOf<ExportItem>()
        for (taskId in selectedIds) {
            val resources = DownloadRepository.queryResourcesForExport(taskId)
            val resource = pickBestResource(resources) ?: continue
            val detail = latestDownloadTaskLiveData.value?.find { it.downloadTask.id == taskId }
            val baseName = detail?.let { "${it.title} - ${it.partTitle}" } ?: "export_$taskId"
            val sourceFile = File(resource.file)
            if (!sourceFile.exists()) continue
            val ext = sourceFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
            items.add(ExportItem("$baseName$ext", resource.mimeType, sourceFile))
        }
        return items
    }

    private fun pickBestResource(resources: List<DownloadResourceEntity>): DownloadResourceEntity? {
        return resources.find { it.type == DownloadResourceType.MIXED }
            ?: resources.find { it.type == DownloadResourceType.VIDEO }
            ?: resources.firstOrNull()
    }

    private fun resolveConflictName(parentDoc: DocumentFile, desiredName: String): String {
        val dotIndex = desiredName.lastIndexOf('.')
        val nameWithoutExt = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
        val ext = if (dotIndex > 0) desiredName.substring(dotIndex) else ""

        if (parentDoc.findFile(desiredName) == null) return nameWithoutExt

        var counter = 1
        while (parentDoc.findFile("$nameWithoutExt($counter)$ext") != null) {
            counter++
        }
        return "$nameWithoutExt($counter)"
    }

    private fun copyFileToUri(
        context: android.content.Context,
        uri: Uri,
        sourceFile: File
    ): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream, bufferSize = 8192)
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}