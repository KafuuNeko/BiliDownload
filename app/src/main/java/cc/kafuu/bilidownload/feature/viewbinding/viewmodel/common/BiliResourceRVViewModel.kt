package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.download.BatchDownloadResolver
import cc.kafuu.bilidownload.common.download.BatchDownloadUseCase
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliResourceModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.VideoDetailsActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/** 管理可下载 B 站资源列表的条目选择和批量下载 UI 状态。 */
open class BiliResourceRVViewModel : BiliRVViewModel() {
    /** 可跨配置变更重放的批量下载选择状态，不持有 Fragment 或 LifecycleOwner。 */
    sealed interface BatchDialogRequest {
        val id: Long

        data class Scope(
            override val id: Long,
            val request: BatchDownloadUseCase.ScopeSelectionRequest,
        ) : BatchDialogRequest

        data class Streams(
            override val id: Long,
            val request: BatchDownloadUseCase.StreamSelectionRequest,
        ) : BatchDialogRequest
    }

    private sealed interface PendingBatchDialog {
        val id: Long

        data class Scope(
            override val id: Long,
            val result: CompletableDeferred<BatchDownloadUseCase.DownloadScope?>,
        ) : PendingBatchDialog

        data class Streams(
            override val id: Long,
            val result: CompletableDeferred<BatchDownloadResolver.StreamSelection?>,
        ) : PendingBatchDialog
    }

    private val mBatchDownloadUseCase = BatchDownloadUseCase()
    private var mNextBatchDialogId = 0L
    private var mPendingBatchDialog: PendingBatchDialog? = null

    private val mMultipleSelectModeLiveData = MutableLiveData(false)
    val multipleSelectModeLiveData = mMultipleSelectModeLiveData.liveData()

    private val mMultipleSelectItemsLiveData =
        MutableLiveData<Set<BiliResourceModel>>(emptySet())
    val multipleSelectItemsLiveData = mMultipleSelectItemsLiveData.liveData()

    private val mBatchDownloadRunningLiveData = MutableLiveData(false)
    val batchDownloadRunningLiveData = mBatchDownloadRunningLiveData.liveData()

    private val mBatchDialogRequestLiveData = MutableLiveData<BatchDialogRequest?>(null)
    val batchDialogRequestLiveData = mBatchDialogRequestLiveData.liveData()

    fun enterDetails(element: BiliVideoModel) {
        startActivity(VideoDetailsActivity::class.java, VideoDetailsActivity.buildIntent(element))
    }

    fun enterDetails(element: BiliMediaModel) {
        startActivity(VideoDetailsActivity::class.java, VideoDetailsActivity.buildIntent(element))
    }

    /** 普通模式下进入详情页，多选模式下切换当前条目的选中状态。 */
    fun onResourceClick(element: BiliResourceModel) {
        if (mBatchDownloadRunningLiveData.value == true) return
        if (mMultipleSelectModeLiveData.value == true) {
            toggleResourceSelection(element)
            return
        }
        when (element) {
            is BiliVideoModel -> enterDetails(element)
            is BiliMediaModel -> enterDetails(element)
        }
    }

    /** 长按条目进入多选模式，并选中触发长按的条目。 */
    fun onResourceLongClick(element: BiliResourceModel): Boolean {
        if (mBatchDownloadRunningLiveData.value == true) return true
        if (mMultipleSelectModeLiveData.value != true) {
            mMultipleSelectModeLiveData.value = true
        }
        toggleResourceSelection(element)
        return true
    }

    fun cancelMultipleSelect() {
        if (mBatchDownloadRunningLiveData.value == true) return
        clearMultipleSelection()
    }

    private fun toggleResourceSelection(element: BiliResourceModel) {
        val selected = mMultipleSelectItemsLiveData.value.orEmpty().toMutableSet()
        if (!selected.add(element)) selected.remove(element)
        mMultipleSelectItemsLiveData.value = selected
        if (selected.isEmpty()) {
            mMultipleSelectModeLiveData.value = false
        }
    }

    private fun clearMultipleSelection() {
        mMultipleSelectModeLiveData.value = false
        mMultipleSelectItemsLiveData.value = emptySet()
    }

    fun onDownloadMultipleSelectItems() {
        if (mBatchDownloadRunningLiveData.value == true) return
        val sources = mMultipleSelectItemsLiveData.value.orEmpty().toList()
        if (sources.isEmpty()) return

        viewModelScope.launch {
            mBatchDownloadRunningLiveData.value = true
            try {
                when (val result = mBatchDownloadUseCase.execute(
                    sources = sources,
                    selectScope = ::selectDownloadScope,
                    selectStreams = ::selectDownloadStreams,
                )) {
                    BatchDownloadUseCase.Result.NoCandidates -> showResolveFailure()
                    BatchDownloadUseCase.Result.Cancelled -> Unit
                    is BatchDownloadUseCase.Result.Completed -> {
                        if (result.requestedPartCount > 0) {
                            showBatchDownloadResult(result.addedCount, result.skippedCount)
                        }
                        clearMultipleSelection()
                    }
                }
            } finally {
                mBatchDownloadRunningLiveData.value = false
            }
        }
    }

    private suspend fun selectDownloadScope(
        request: BatchDownloadUseCase.ScopeSelectionRequest
    ): BatchDownloadUseCase.DownloadScope? {
        check(mPendingBatchDialog == null) { "A batch dialog is already pending" }
        val pending = PendingBatchDialog.Scope(
            id = ++mNextBatchDialogId,
            result = CompletableDeferred(),
        )
        mPendingBatchDialog = pending
        mBatchDialogRequestLiveData.value = BatchDialogRequest.Scope(pending.id, request)
        return try {
            pending.result.await()
        } finally {
            clearBatchDialog(pending)
        }
    }

    private suspend fun selectDownloadStreams(
        request: BatchDownloadUseCase.StreamSelectionRequest
    ): BatchDownloadResolver.StreamSelection? {
        check(mPendingBatchDialog == null) { "A batch dialog is already pending" }
        val pending = PendingBatchDialog.Streams(
            id = ++mNextBatchDialogId,
            result = CompletableDeferred(),
        )
        mPendingBatchDialog = pending
        mBatchDialogRequestLiveData.value = BatchDialogRequest.Streams(pending.id, request)
        return try {
            pending.result.await()
        } finally {
            clearBatchDialog(pending)
        }
    }

    fun onDownloadScopeSelected(
        requestId: Long,
        scope: BatchDownloadUseCase.DownloadScope?,
    ) {
        // 页面重建前的旧 Dialog 可能延迟返回；类型和 ID 必须同时匹配当前请求。
        val pending = mPendingBatchDialog as? PendingBatchDialog.Scope ?: return
        if (pending.id == requestId) pending.result.complete(scope)
    }

    fun onDownloadStreamsSelected(
        requestId: Long,
        streams: BatchDownloadResolver.StreamSelection?,
    ) {
        // 页面重建前的旧 Dialog 可能延迟返回；类型和 ID 必须同时匹配当前请求。
        val pending = mPendingBatchDialog as? PendingBatchDialog.Streams ?: return
        if (pending.id == requestId) pending.result.complete(streams)
    }

    private fun clearBatchDialog(pending: PendingBatchDialog) {
        if (mPendingBatchDialog !== pending) return
        mPendingBatchDialog = null
        mBatchDialogRequestLiveData.value = null
    }

    private fun showResolveFailure() {
        popMessage(
            ToastMessageAction(
                CommonLibs.getString(R.string.text_batch_resolve_failed),
                Toast.LENGTH_LONG,
            )
        )
    }

    private fun showBatchDownloadResult(addedCount: Int, skippedCount: Int) {
        popMessage(
            ToastMessageAction(
                CommonLibs.getString(
                    R.string.text_batch_download_result,
                    addedCount,
                    skippedCount,
                ),
                Toast.LENGTH_LONG,
            )
        )
    }
}
