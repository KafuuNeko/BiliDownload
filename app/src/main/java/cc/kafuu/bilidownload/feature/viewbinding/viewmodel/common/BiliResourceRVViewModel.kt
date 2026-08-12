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
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.BiliPartDialog
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 管理可下载 B 站资源列表的条目选择和批量下载 UI 状态。 */
open class BiliResourceRVViewModel : BiliRVViewModel() {
    private val mBatchDownloadUseCase = BatchDownloadUseCase()

    private val mMultipleSelectModeLiveData = MutableLiveData(false)
    val multipleSelectModeLiveData = mMultipleSelectModeLiveData.liveData()

    private val mMultipleSelectItemsLiveData =
        MutableLiveData<Set<BiliResourceModel>>(emptySet())
    val multipleSelectItemsLiveData = mMultipleSelectItemsLiveData.liveData()

    private val mBatchDownloadRunningLiveData = MutableLiveData(false)
    val batchDownloadRunningLiveData = mBatchDownloadRunningLiveData.liveData()

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
        val message = CommonLibs.getString(
            R.string.text_batch_download_scope_message,
            request.sourceCount,
            request.totalPartCount,
            request.resolveFailureCount,
        )
        return suspendCancellableCoroutine { continuation ->
            popDialog(
                dialog = ConfirmDialog.buildDialog(
                    title = CommonLibs.getString(R.string.text_batch_download_scope_title),
                    message = message,
                    leftButtonText = CommonLibs.getString(R.string.text_download_default_part),
                    rightButtonText = CommonLibs.getString(R.string.text_download_all_parts),
                ),
                success = {
                    if (continuation.isActive) {
                        val scope = if (it as Boolean) {
                            BatchDownloadUseCase.DownloadScope.ALL_PARTS
                        } else {
                            BatchDownloadUseCase.DownloadScope.PREFERRED_PART
                        }
                        continuation.resume(scope)
                    }
                },
                failed = {
                    if (continuation.isActive) continuation.resume(null)
                },
            )
        }
    }

    private suspend fun selectDownloadStreams(
        request: BatchDownloadUseCase.StreamSelectionRequest
    ): BatchDownloadResolver.StreamSelection? =
        suspendCancellableCoroutine { continuation ->
            popDialog(
                dialog = BiliPartDialog.buildDialog(
                    request.partTitle
                        ?: CommonLibs.getString(R.string.text_select_the_resource_to_download),
                    request.dash.video,
                    request.dash.getAllAudio(),
                ),
                success = {
                    if (continuation.isActive) {
                        val result = it as BiliPartDialog.Companion.Result
                        continuation.resume(
                            BatchDownloadResolver.StreamSelection(
                                result.videoStream,
                                result.audioStream,
                            )
                        )
                    }
                },
                failed = {
                    if (continuation.isActive) continuation.resume(null)
                },
            )
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
