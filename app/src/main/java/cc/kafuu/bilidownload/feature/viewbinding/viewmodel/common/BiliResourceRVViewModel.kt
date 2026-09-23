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
import cc.kafuu.bilidownload.common.model.bili.VideoStats
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.repository.BiliVideoStatsRepository
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.VideoDetailsActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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

    private val mVideoStatsLiveData = MutableLiveData<Map<String, VideoStats>>(emptyMap())
    val videoStatsLiveData = mVideoStatsLiveData.liveData()
    private val mStatsJobs = mutableMapOf<String, Job>()
    private var mStatsGeneration = 0L

    /**
     * 为滚动稳定后的可见稿件补齐缺失项，已有完整统计的条目不发请求。
     * 页面只发布独立快照，保持原视频对象及其多选身份不变。
     */
    fun loadVisibleVideoStats(videos: List<BiliVideoModel>) {
        pauseVideoStatsRequests()
        val generation = mStatsGeneration
        val repository = NetworkManager.biliVideoStatsRepository
        val uniqueVideos = videos.distinctBy { it.bvid }
        val cached = uniqueVideos.mapNotNull { video ->
            repository.getCached(video.bvid)?.let { video.bvid to it }
        }.toMap()
        val visibleIds = uniqueVideos.map { it.bvid }.toSet()
        // 过期缓存可以继续展示到新结果到达，但不能用于跳过本次刷新请求。
        mVideoStatsLiveData.value = mVideoStatsLiveData.value.orEmpty()
            .filterKeys { it in visibleIds } + cached

        // 缓存和列表字段一起判断完整性，避免为另一入口已经取得的数据重复补齐。
        uniqueVideos.filter { !it.stats.withFallback(cached[it.bvid]).isComplete }.forEach { video ->
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = repository.getStats(video.bvid)
                    if (generation == mStatsGeneration && result is BiliVideoStatsRepository.Result.Success) {
                        mVideoStatsLiveData.value = mVideoStatsLiveData.value.orEmpty() +
                            (video.bvid to result.stats)
                    }
                } finally {
                    if (generation == mStatsGeneration) mStatsJobs.remove(video.bvid)
                }
            }
            mStatsJobs[video.bvid] = job
            job.start()
        }
    }

    /** 滚动、换页或视图暂停时释放补齐订阅，保留已显示快照以免卡片闪烁。 */
    fun pauseVideoStatsRequests() {
        mStatsGeneration++
        mStatsJobs.values.forEach { it.cancel() }
        mStatsJobs.clear()
    }

    /** 账号变化时清除页面快照；共享仓库已由账号生命周期同步失效。 */
    fun clearVideoStats() {
        pauseVideoStatsRequests()
        mVideoStatsLiveData.value = emptyMap()
    }

    /** 将列表已展示的补齐统计随原稿件传入详情，避免页面打开后先退回未知值。 */
    fun enterDetails(element: BiliVideoModel) {
        val stats = element.stats.withFallback(mVideoStatsLiveData.value?.get(element.bvid))
        startActivity(VideoDetailsActivity::class.java, VideoDetailsActivity.buildIntent(element, stats))
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
