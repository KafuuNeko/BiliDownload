package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.download.BatchDeleteUseCase
import cc.kafuu.bilidownload.common.download.BatchExportUseCase
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.HistoryDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.RVViewModel
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class HistoryViewModel : RVViewModel() {
    val centerCrop = CenterCrop()

    private val mBatchDeleteUseCase = BatchDeleteUseCase()
    private val mBatchExportUseCase = BatchExportUseCase()
    private var mDisplayedTasks: List<DownloadTaskWithVideoDetails> = emptyList()

    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskWithVideoDetails>>
        private set

    private val mMultiSelectUiStateLiveData = MutableLiveData(HistoryMultiSelectUiState())
    val multiSelectUiStateLiveData = mMultiSelectUiStateLiveData.liveData()

    // 批量导出进度
    private val mBatchExportProgressLiveData =
        MutableLiveData<BatchExportUseCase.Progress?>(null)
    val batchExportProgressLiveData = mBatchExportProgressLiveData.liveData()

    companion object {
        class RequestExportDirAction : ViewAction()
    }

    fun initData(status: List<TaskStatus>) {
        if (::latestDownloadTaskLiveData.isInitialized) return
        latestDownloadTaskLiveData = DownloadRepository.queryDownloadTasksDetailsLiveData(status)
    }

    fun updateHistoryList(tasks: List<DownloadTaskWithVideoDetails>) {
        mDisplayedTasks = tasks
        updateList(tasks.toMutableList())
        updateMultiSelectState {
            it.updateAvailableIds(tasks.mapTo(mutableSetOf()) { task -> task.downloadTask.id })
        }
    }

    fun getStatusIcon(task: DownloadTaskWithVideoDetails) = CommonLibs.getDrawable(
        when (TaskStatus.entries.find { it.code == task.downloadTask.status }) {
            TaskStatus.PREPARE -> R.drawable.ic_prepare
            TaskStatus.DOWNLOADING -> R.drawable.ic_downloading
            TaskStatus.DOWNLOAD_FAILED -> R.drawable.ic_download_failed_cloud
            TaskStatus.SYNTHESIS -> R.drawable.ic_synthesis
            TaskStatus.SYNTHESIS_FAILED -> R.drawable.ic_synthesis_failed
            TaskStatus.COMPLETED -> R.drawable.ic_download_done_cloud
            TaskStatus.PUBLISHING -> R.drawable.ic_synthesis
            TaskStatus.PUBLISH_FAILED -> R.drawable.ic_download_failed_cloud
            else -> R.drawable.ic_unknown_med
        }
    )

    fun getStatusText(task: DownloadTaskWithVideoDetails): String {
        val percent = task.downloadTask.groupId?.let {
            DownloadManager.getSnapshot(it)?.percent
        }
        return "${percent ?: 0}%"
    }

    fun entryHistoryDetails(task: DownloadTaskWithVideoDetails) {
        if (currentMultiSelectState().isEnabled) {
            toggleItemSelection(task.downloadTask.id)
            return
        }
        startActivity(
            HistoryDetailsActivity::class.java,
            HistoryDetailsActivity.buildIntent(task.downloadTask.id)
        )
    }

    fun onItemLongClick(task: DownloadTaskWithVideoDetails): Boolean {
        if (currentMultiSelectState().isEnabled) return false
        toggleItemSelection(task.downloadTask.id)
        return true
    }

    fun exitMultiSelectMode() {
        updateMultiSelectState(HistoryMultiSelectUiState::clearSelection)
    }

    fun toggleItemSelection(taskId: Long) {
        updateMultiSelectState { it.toggleItem(taskId) }
    }

    fun toggleSelectAll() {
        updateMultiSelectState(HistoryMultiSelectUiState::toggleAll)
    }

    fun getSelectedCount(): Int = currentMultiSelectState().selectedIds.size

    suspend fun deleteSelectedTasks() {
        val selectedIds = currentMultiSelectState().selectedIds
        if (selectedIds.isEmpty()) return
        val targets = mDisplayedTasks.mapNotNull { task ->
            val taskId = task.downloadTask.id
            if (taskId !in selectedIds) return@mapNotNull null
            BatchDeleteUseCase.Target(
                taskId = taskId,
                groupId = task.downloadTask.groupId,
            )
        }
        if (mBatchDeleteUseCase.execute(targets).hasFailure) {
            popMessage(
                ToastMessageAction(CommonLibs.getString(R.string.delete_resource_failed_message))
            )
        }
        exitMultiSelectMode()
    }

    fun tryBatchExport() {
        if (!currentMultiSelectState().hasSelection) return
        sendViewAction(RequestExportDirAction())
    }

    suspend fun executeBatchExport(treeUri: Uri) {
        val selectedIds = currentMultiSelectState().selectedIds
        if (selectedIds.isEmpty()) return
        val sources = mDisplayedTasks.mapNotNull { task ->
            val taskId = task.downloadTask.id
            if (taskId !in selectedIds) return@mapNotNull null
            BatchExportUseCase.Source(
                taskId = taskId,
                displayName = "${task.title} - ${task.partTitle}",
            )
        }

        val result = try {
            mBatchExportUseCase.execute(treeUri, sources) { progress ->
                mBatchExportProgressLiveData.postValue(progress)
            }
        } catch (_: Exception) {
            BatchExportUseCase.Result.InvalidDestination
        } finally {
            mBatchExportProgressLiveData.postValue(null)
        }

        when (result) {
            BatchExportUseCase.Result.NoExportableResources -> popMessage(
                ToastMessageAction(CommonLibs.getString(R.string.batch_export_no_resource_message))
            )

            BatchExportUseCase.Result.InvalidDestination -> showBatchExportFailure()

            is BatchExportUseCase.Result.Completed -> {
                if (result.successCount > 0) {
                    popMessage(
                        ToastMessageAction(
                            CommonLibs.getString(
                                R.string.batch_export_success_message,
                                result.successCount,
                            )
                        )
                    )
                } else {
                    showBatchExportFailure()
                }
                exitMultiSelectMode()
            }
        }
    }

    private fun showBatchExportFailure() {
        popMessage(
            ToastMessageAction(
                CommonLibs.getString(
                    R.string.batch_export_failed_message,
                    CommonLibs.getString(R.string.error_unknown),
                )
            )
        )
    }

    private fun currentMultiSelectState(): HistoryMultiSelectUiState =
        mMultiSelectUiStateLiveData.value ?: HistoryMultiSelectUiState()

    private fun updateMultiSelectState(
        transform: (HistoryMultiSelectUiState) -> HistoryMultiSelectUiState
    ) {
        val current = currentMultiSelectState()
        val updated = transform(current)
        if (updated != current) mMultiSelectUiStateLiveData.value = updated
    }
}
