package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.download.BatchDownloadResolver
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.BatchQualityMismatchMode
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.model.bili.BiliFavoriteModel
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliResourceModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.FavoriteDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.VideoDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.BiliPartDialog
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

open class BiliRVViewModel : RVViewModel() {
    private val mMultipleSelectModeLiveData = MutableLiveData(false)
    val multipleSelectModeLiveData = mMultipleSelectModeLiveData.liveData()

    private val mMultipleSelectItemsLiveData =
        MutableLiveData<Set<BiliResourceModel>>(emptySet())
    val multipleSelectItemsLiveData = mMultipleSelectItemsLiveData.liveData()

    private val mBatchDownloadRunningLiveData = MutableLiveData(false)
    val batchDownloadRunningLiveData = mBatchDownloadRunningLiveData.liveData()

    /**
     * 刷新数据列表。
     */
    open fun onRefreshData(
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) = Unit

    /**
     * 加载更多数据。
     */
    open fun onLoadMoreData(
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) = Unit

    /**
     * 进入视频详情页。
     */
    fun enterDetails(element: BiliVideoModel) {
        startActivity(VideoDetailsActivity::class.java, VideoDetailsActivity.buildIntent(element))
    }

    /**
     * 进入剧集详情页。
     */
    fun enterDetails(element: BiliMediaModel) {
        startActivity(VideoDetailsActivity::class.java, VideoDetailsActivity.buildIntent(element))
    }

    /**
     * 进入收藏夹详情页。
     */
    fun enterDetails(element: BiliFavoriteModel) {
        startActivity(
            FavoriteDetailsActivity::class.java,
            FavoriteDetailsActivity.buildIntent(element)
        )
    }

    /**
     * 处理稿件或剧集条目的点击事件。
     *
     * 普通模式下进入详情页，多选模式下切换当前条目的选中状态。
     */
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

    /**
     * 长按条目进入多选模式，并选中触发长按的条目。
     */
    fun onResourceLongClick(element: BiliResourceModel): Boolean {
        if (mBatchDownloadRunningLiveData.value == true) return true
        if (mMultipleSelectModeLiveData.value != true) {
            mMultipleSelectModeLiveData.value = true
        }
        toggleResourceSelection(element)
        return true
    }

    /**
     * 退出多选模式并清空选择。
     */
    fun cancelMultipleSelect() {
        if (mBatchDownloadRunningLiveData.value == true) return
        mMultipleSelectModeLiveData.value = false
        mMultipleSelectItemsLiveData.value = emptySet()
    }

    private fun toggleResourceSelection(element: BiliResourceModel) {
        val selected = mMultipleSelectItemsLiveData.value.orEmpty().toMutableSet()
        if (!selected.add(element)) selected.remove(element)
        mMultipleSelectItemsLiveData.value = selected
        if (selected.isEmpty()) {
            mMultipleSelectModeLiveData.value = false
        }
    }

    /**
     * 解析选中的列表条目、确认多分 P 下载范围，并批量加入下载队列。
     */
    fun onDownloadMultipleSelectItems() {
        if (mBatchDownloadRunningLiveData.value == true) return
        val sources = mMultipleSelectItemsLiveData.value.orEmpty().toList()
        if (sources.isEmpty()) return

        viewModelScope.launch {
            mBatchDownloadRunningLiveData.value = true
            try {
                val resolveResult = BatchDownloadResolver.resolve(sources)
                if (resolveResult.candidates.isEmpty()) {
                    popMessage(
                        ToastMessageAction(
                            CommonLibs.getString(R.string.text_batch_resolve_failed),
                            Toast.LENGTH_LONG
                        )
                    )
                    return@launch
                }

                val downloadAll = confirmDownloadScope(resolveResult) ?: return@launch
                val parts = resolveResult.candidates.flatMap { candidate ->
                    if (downloadAll) candidate.parts else listOf(candidate.preferredPart)
                }.distinctBy { it.bvid to it.cid }
                if (!enqueueParts(parts, resolveResult.failures.size)) return@launch
                mMultipleSelectModeLiveData.value = false
                mMultipleSelectItemsLiveData.value = emptySet()
            } finally {
                mBatchDownloadRunningLiveData.value = false
            }
        }
    }

    /**
     * 仅当选中内容包含多分 P 稿件或多集媒体时询问用户下载范围。
     */
    private suspend fun confirmDownloadScope(
        result: BatchDownloadResolver.ResolveResult
    ): Boolean? {
        val hasMultipleParts = result.candidates.any { it.parts.size > 1 }
        if (!hasMultipleParts) return false

        val totalParts = result.candidates.sumOf { it.parts.size }
        val message = CommonLibs.getString(
            R.string.text_batch_download_scope_message,
            result.candidates.size,
            totalParts,
            result.failures.size
        )
        return suspendCancellableCoroutine { continuation ->
            popDialog(
                dialog = ConfirmDialog.buildDialog(
                    title = CommonLibs.getString(R.string.text_batch_download_scope_title),
                    message = message,
                    leftButtonText = CommonLibs.getString(R.string.text_download_default_part),
                    rightButtonText = CommonLibs.getString(R.string.text_download_all_parts)
                ),
                success = {
                    if (continuation.isActive) continuation.resume(it as Boolean)
                },
                failed = {
                    if (continuation.isActive) continuation.resume(null)
                }
            )
        }
    }

    private suspend fun enqueueParts(
        parts: List<BiliVideoPartModel>,
        resolveFailureCount: Int
    ): Boolean {
        if (parts.isEmpty()) return true

        var skippedCount = resolveFailureCount
        var firstPlayableIndex = -1
        var firstDash: BiliPlayStreamDash? = null
        for ((index, part) in parts.withIndex()) {
            when (val result = loadPartDash(part)) {
                is ResultWrapper.Success -> {
                    firstPlayableIndex = index
                    firstDash = result.value
                    break
                }

                is ResultWrapper.Error -> skippedCount++
            }
        }
        val selectionDash = firstDash ?: run {
            showBatchDownloadResult(addedCount = 0, skippedCount = skippedCount)
            return true
        }
        val selectedStreams = selectDownloadStreams(selectionDash) ?: return false

        var addedCount = 0
        for (index in firstPlayableIndex until parts.size) {
            val part = parts[index]
            val dash = if (index == firstPlayableIndex) {
                selectionDash
            } else {
                when (val result = loadPartDash(part)) {
                    is ResultWrapper.Success -> result.value
                    is ResultWrapper.Error -> {
                        skippedCount++
                        continue
                    }
                }
            }

            val streams = resolveBatchStreams(part, dash, selectedStreams)
            if (streams == null) {
                skippedCount++
                continue
            }

            val resources = buildDashModels(streams.videoStream, streams.audioStream)
            if (resources.isEmpty()) {
                skippedCount++
                continue
            }
            val taskCreated = runCatching {
                DownloadManager.startDownload(
                    CommonLibs.requireContext(),
                    part.bvid,
                    part.cid,
                    resources
                )
            }.getOrDefault(false)
            if (taskCreated) {
                addedCount++
            } else {
                skippedCount++
            }
        }

        showBatchDownloadResult(addedCount, skippedCount)
        return true
    }

    private suspend fun resolveBatchStreams(
        part: BiliVideoPartModel,
        dash: BiliPlayStreamDash,
        preferred: BiliPartDialog.Companion.Result
    ): BatchDownloadResolver.StreamSelection? {
        BatchDownloadResolver.selectExactStreams(
            preferred.videoStream,
            preferred.audioStream,
            dash
        )?.let { return it }

        return when (AppModel.batchQualityMismatchMode) {
            BatchQualityMismatchMode.AUTO_FALLBACK ->
                BatchDownloadResolver.selectCompatibleStreams(
                    preferred.videoStream,
                    preferred.audioStream,
                    dash
                )

            BatchQualityMismatchMode.ASK -> selectDownloadStreams(
                dash = dash,
                title = part.name
            )?.let {
                BatchDownloadResolver.StreamSelection(it.videoStream, it.audioStream)
            }

            BatchQualityMismatchMode.SKIP -> null
        }
    }

    private fun showBatchDownloadResult(addedCount: Int, skippedCount: Int) {
        popMessage(
            ToastMessageAction(
                CommonLibs.getString(
                    R.string.text_batch_download_result,
                    addedCount,
                    skippedCount
                ),
                Toast.LENGTH_LONG
            )
        )
    }

    private fun buildDashModels(
        videoStream: BiliPlayStreamResource?,
        audioStream: BiliPlayStreamResource?
    ) = buildList {
        videoStream?.let { add(BiliDashModel.create(DashType.VIDEO, it)) }
        audioStream?.let { add(BiliDashModel.create(DashType.AUDIO, it)) }
    }

    private suspend fun loadPartDash(
        part: BiliVideoPartModel
    ): ResultWrapper<BiliPlayStreamDash, String> =
        suspendCancellableCoroutine { continuation ->
        NetworkManager.biliVideoRepository.requestPlayStreamDash(
            part.bvid,
            part.cid,
            object : IServerCallback<BiliPlayStreamDash> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: BiliPlayStreamDash
                ) {
                    if (continuation.isActive) {
                        continuation.resume(ResultWrapper.Success(data))
                    }
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    if (continuation.isActive) {
                        continuation.resume(ResultWrapper.Error(message))
                    }
                }
            }
        )
    }

    private suspend fun selectDownloadStreams(
        dash: BiliPlayStreamDash,
        title: String = CommonLibs.getString(R.string.text_select_the_resource_to_download)
    ): BiliPartDialog.Companion.Result? = suspendCancellableCoroutine { continuation ->
        popDialog(
            dialog = BiliPartDialog.buildDialog(
                title,
                dash.video,
                dash.getAllAudio()
            ),
            success = {
                if (continuation.isActive) {
                    continuation.resume(it as BiliPartDialog.Companion.Result)
                }
            },
            failed = {
                if (continuation.isActive) continuation.resume(null)
            }
        )
    }
}
