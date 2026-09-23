package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
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
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.HistoryDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.RVViewModel
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 管理下载历史选择和导出快照，目录选择与文件复制拥有明确的互斥状态。 */
class HistoryViewModel(
    private val mBatchExportUseCase: BatchExportUseCase = BatchExportUseCase(),
) : RVViewModel() {
    val centerCrop = CenterCrop()

    private val mBatchDeleteUseCase = BatchDeleteUseCase()
    private var mDisplayedTasks: List<DownloadTaskWithVideoDetails> = emptyList()

    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskWithVideoDetails>>
        private set

    private val mMultiSelectUiStateLiveData = MutableLiveData(HistoryMultiSelectUiState())
    val multiSelectUiStateLiveData = mMultiSelectUiStateLiveData.liveData()

    private val mExportUiStateLiveData = MutableLiveData<HistoryExportUiState>(HistoryExportUiState.Idle)
    val exportUiStateLiveData = mExportUiStateLiveData.liveData()
    private var mPendingExportSources: List<BatchExportUseCase.Source> = emptyList()
    private var mNextExportRequestId = 0L

    companion object {
        /** 一次系统目录选择；宿主须先按 ID 消费请求，避免 LiveData 在重建时重放。 */
        class RequestExportDirAction(val requestId: Long) : ViewAction()
    }

    fun initData(status: List<TaskStatus>) {
        if (::latestDownloadTaskLiveData.isInitialized) return
        latestDownloadTaskLiveData = DownloadRepository.queryDownloadTasksDetailsLiveData(status)
    }

    /** 展示数据与可选身份使用同一快照，先准备选择状态再通知列表绑定。 */
    fun updateHistoryList(tasks: List<DownloadTaskWithVideoDetails>) {
        mDisplayedTasks = tasks.toList()
        updateMultiSelectState {
            it.updateAvailableIds(tasks.mapTo(mutableSetOf()) { task -> task.downloadTask.id })
        }
        updateList(tasks.toMutableList())
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

    /** 多选时切换当前项，导出期间保持固定批次，普通模式进入资源详情。 */
    fun entryHistoryDetails(task: DownloadTaskWithVideoDetails) {
        if (isExportBusy()) return
        if (currentMultiSelectState().isEnabled) {
            toggleItemSelection(task.downloadTask.id)
            return
        }
        startActivity(
            HistoryDetailsActivity::class.java,
            HistoryDetailsActivity.buildIntent(task.downloadTask.id)
        )
    }

    /** 长按统一消费触摸并切换当前任务，防止多选时落回普通点击。 */
    fun onItemLongClick(task: DownloadTaskWithVideoDetails): Boolean {
        if (isExportBusy()) return true
        toggleItemSelection(task.downloadTask.id)
        return true
    }

    /** 空闲时退出多选，选择目录或复制期间保留批次状态。 */
    fun exitMultiSelectMode() {
        if (isExportBusy()) return
        updateMultiSelectState(HistoryMultiSelectUiState::clearSelection)
    }

    /** 按任务 ID 切换选择；导出期间忽略列表触摸。 */
    fun toggleItemSelection(taskId: Long) {
        if (isExportBusy()) return
        updateMultiSelectState { it.toggleItem(taskId) }
    }

    /** 空闲时在当前列表的全选与清空之间切换。 */
    fun toggleSelectAll() {
        if (isExportBusy()) return
        updateMultiSelectState(HistoryMultiSelectUiState::toggleAll)
    }

    fun getSelectedCount(): Int = currentMultiSelectState().selectedIds.size

    /** 固定待删除目标；正在选择导出目录或复制时不执行删除。 */
    suspend fun deleteSelectedTasks() {
        if (isExportBusy()) return
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

    /** 请求目录前固定导出目标，列表刷新不会改变用户已确认的批次。 */
    fun tryBatchExport() {
        if (isExportBusy()) return
        val selectedIds = currentMultiSelectState().selectedIds
        mPendingExportSources = mDisplayedTasks.filter { it.downloadTask.id in selectedIds }.map { task ->
            BatchExportUseCase.Source(
                taskId = task.downloadTask.id,
                fileNameContext = DownloadFileNameUtils.TemplateContext(
                    videoName = task.title.ifBlank { task.downloadTask.biliBvid },
                    partName = task.partTitle.ifBlank { task.downloadTask.biliCid.toString() },
                ),
            )
        }
        if (mPendingExportSources.isEmpty()) return
        // 进入互斥状态后再发送一次性事件，重复点击不会覆盖导出快照。
        val requestId = ++mNextExportRequestId
        mExportUiStateLiveData.value = HistoryExportUiState.SelectingDirectory(requestId)
        sendViewAction(RequestExportDirAction(requestId))
    }

    /** 目录选择事件只交付一次，屏幕旋转后不重复打开系统选择器。 */
    fun consumeExportDirectoryRequest(requestId: Long): Boolean {
        val state = mExportUiStateLiveData.value as? HistoryExportUiState.SelectingDirectory ?: return false
        if (state.requestId != requestId || !state.launchPending) return false
        mExportUiStateLiveData.value = state.copy(launchPending = false)
        return true
    }

    /** 接收系统目录选择或取消；复制属于 ViewModel，旋转不会中断正在导出的文件。 */
    fun onExportDirectorySelected(treeUri: Uri?) {
        if (mExportUiStateLiveData.value !is HistoryExportUiState.SelectingDirectory) return
        val sources = mPendingExportSources
        mPendingExportSources = emptyList()
        if (treeUri == null) {
            mExportUiStateLiveData.value = HistoryExportUiState.Idle
            return
        }
        mExportUiStateLiveData.value = HistoryExportUiState.Exporting(BatchExportUseCase.Progress(0, sources.size))
        viewModelScope.launch {
            try {
                // 进度切回主线程后再继续，避免完成状态被 IO 线程的迟到 postValue 覆盖。
                val result = mBatchExportUseCase.execute(treeUri, sources) { progress ->
                    withContext(Dispatchers.Main.immediate) {
                        mExportUiStateLiveData.value = HistoryExportUiState.Exporting(progress)
                    }
                }
                showExportResult(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showBatchExportFailure()
            } finally {
                mExportUiStateLiveData.value = HistoryExportUiState.Idle
            }
        }
    }

    /** 部分成功准确展示跳过和失败数量，完成后清空本次选择。 */
    private fun showExportResult(result: BatchExportUseCase.Result) {
        val message = when (result) {
            BatchExportUseCase.Result.NoExportableResources -> CommonLibs.getString(R.string.batch_export_no_resource_message)
            BatchExportUseCase.Result.InvalidDestination -> CommonLibs.getString(R.string.batch_export_invalid_destination)
            is BatchExportUseCase.Result.Completed -> {
                updateMultiSelectState(HistoryMultiSelectUiState::clearSelection)
                CommonLibs.getString(R.string.batch_export_result, result.successCount, result.skippedCount, result.failedCount)
            }
        }
        popMessage(ToastMessageAction(message))
    }

    private fun isExportBusy(): Boolean = mExportUiStateLiveData.value != HistoryExportUiState.Idle

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
