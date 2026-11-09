package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.core.viewbinding.CoreViewModel
import cc.kafuu.bilidownload.common.download.BatchDownloadUseCase
import cc.kafuu.bilidownload.common.download.BatchDownloadResolver
import cc.kafuu.bilidownload.common.ext.limit
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliResourceModel
import cc.kafuu.bilidownload.common.model.bili.BiliUpData
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.model.bili.VideoStats
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliXmlDanmaku
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.model.BiliSubtitleListContainer
import cc.kafuu.bilidownload.common.network.model.BccSubtitle
import cc.kafuu.bilidownload.common.notes.NotesPdfGenerator
import cc.kafuu.bilidownload.common.utils.DanmakuExportUtils
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.SubtitleExportUtils
import cc.kafuu.bilidownload.common.utils.TimeUtils
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.PersonalDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.BiliPartDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import kotlin.coroutines.resume

/** 管理视频详情、分 P 选择与页面内批量建任务；已入队任务由下载服务继续执行。 */
class VideoDetailsViewModel(
    private val mBatchDownloadUseCase: BatchDownloadUseCase = BatchDownloadUseCase(),
) : CoreViewModel() {
    companion object {
        class SaveCoverAction(
            val coverUrl: String,
            val fileName: String
        ) : ViewAction()

        class ShowSaveCoverConfirmAction(
            val coverUrl: String,
            val bvid: String?
        ) : ViewAction()

        class ShowDownloadDanmakuConfirmAction(
            val part: BiliVideoPartModel
        ) : ViewAction()

        class ShowDownloadSubtitleConfirmAction(
            val part: BiliVideoPartModel
        ) : ViewAction()

        class ExportDanmakuAction(val danmakuList: List<BiliXmlDanmaku>) : ViewAction()

        class ExportSubtitleAction(val bccSubtitle: BccSubtitle) : ViewAction()

        class ShowGenerateNotesConfirmAction(
            val part: BiliVideoPartModel
        ) : ViewAction()

        class ExportNotesAction(
            val pdfFile: File,
            val fileName: String
        ) : ViewAction()
    }

    private val mLoadingStatusLiveData = MutableLiveData(LoadingStatus.waitStatus())
    val loadingStatusLiveData = mLoadingStatusLiveData.liveData()

    private val mBiliResourceModelLiveData = MutableLiveData<BiliResourceModel>()
    val biliResourceModelLiveData = mBiliResourceModelLiveData.liveData()

    private val mVideoStatsLiveData = MutableLiveData<VideoStats?>(null)
    val videoStatsLiveData = mVideoStatsLiveData.liveData()

    private val mBiliVideoPageListLiveData = MutableLiveData<List<BiliVideoPartModel>>()
    val biliVideoPageListLiveData = mBiliVideoPageListLiveData.liveData()

    private val mBiliUpDataLiveData = MutableLiveData<BiliUpData?>(null)
    val biliUpDataLiveData = mBiliUpDataLiveData.liveData()

    // 选中的片段
    private val mSelectedVideoPartLiveData = MutableLiveData<BiliVideoPartModel?>()
    val selectedVideoPartLiveData = mSelectedVideoPartLiveData.liveData()

    // 正在加载视频流数据的片段
    private val mLoadingVideoPartLiveData = MutableLiveData<BiliVideoPartModel?>()
    val loadingVideoPartLiveData = mLoadingVideoPartLiveData.liveData()

    private val mPartSelectionLiveData = MutableLiveData(VideoPartSelectionUiState())
    val partSelectionLiveData = mPartSelectionLiveData.liveData()

    private var mBatchDownloadJob: Job? = null
    private val mBatchProgressLiveData = MutableLiveData<BatchDownloadUseCase.Progress?>(null)
    val batchProgressLiveData = mBatchProgressLiveData.liveData()

    /** 可重放的规格选择请求；ID 用于拒绝页面重建前的迟到结果。 */
    data class BatchStreamRequest(
        val id: Long,
        val request: BatchDownloadUseCase.StreamSelectionRequest
    )

    private var mNextBatchRequestId = 0L
    private var mPendingBatchStreams: CompletableDeferred<BatchDownloadResolver.StreamSelection?>? =
        null
    private val mBatchStreamRequestLiveData = MutableLiveData<BatchStreamRequest?>(null)
    val batchStreamRequestLiveData = mBatchStreamRequestLiveData.liveData()

    // 最近被改变状态的列表索引
    private val mLatestChangeIndexLiveData = MutableLiveData(-1)
    val latestChangeIndexLiveData = mLatestChangeIndexLiveData.liveData()

    // 正在下载弹幕的片段
    private val mDownloadingDanmakuPartLiveData = MutableLiveData<BiliVideoPartModel?>()
    val downloadingDanmakuPartLiveData = mDownloadingDanmakuPartLiveData.liveData()

    // 正在下载字幕的片段
    private val mDownloadingSubtitlePartLiveData = MutableLiveData<BiliVideoPartModel?>()
    val downloadingSubtitlePartLiveData = mDownloadingSubtitlePartLiveData.liveData()

    // 正在生成图文笔记的片段
    private val mGeneratingNotesPartLiveData = MutableLiveData<BiliVideoPartModel?>()
    val generatingNotesPartLiveData = mGeneratingNotesPartLiveData.liveData()

    /** 首次加载番剧分集，配置重建时保留正在处理的列表与选择。 */
    fun initData(media: BiliMediaModel) {
        if (mBiliResourceModelLiveData.value != null) return
        mLoadingStatusLiveData.value = LoadingStatus.loadingStatus()
        mBiliResourceModelLiveData.value = media
        mVideoStatsLiveData.value = null

        val callback = object : IServerCallback<BiliSeasonData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliSeasonData
            ) {
                updateParts(parts = data.episodes.map {
                    BiliVideoPartModel(
                        bvid = it.bvid,
                        cid = it.cid,
                        name = "${it.title} ${it.longTitle}",
                        remark = it.badge
                    )
                })
                mLoadingStatusLiveData.value = LoadingStatus.doneStatus()
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                mLoadingStatusLiveData.value = LoadingStatus.errorStatus(
                    message = message
                )
            }
        }
        if (media.seasonId != 0L) {
            NetworkManager.biliVideoRepository.requestSeasonDetailBySeasonId(
                seasonId = media.seasonId,
                callback = callback
            )
        } else {
            NetworkManager.biliVideoRepository.requestSeasonDetailByEpId(
                epId = media.mediaId,
                callback = callback
            )
        }
    }

    /** 先展示列表快照；详情响应成功后更新为最新统计，接口缺项沿用已有数值。 */
    fun initData(video: BiliVideoModel, stats: VideoStats = video.stats) {
        if (mBiliResourceModelLiveData.value != null) return
        mLoadingStatusLiveData.value = LoadingStatus.loadingStatus()
        mBiliResourceModelLiveData.value = video
        mVideoStatsLiveData.value = stats.normalized()
        val callback = object : IServerCallback<BiliVideoData> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: BiliVideoData) {
                mVideoStatsLiveData.value = data.stat?.toVideoStats()
                    ?.withFallback(fallback = mVideoStatsLiveData.value)
                    ?: mVideoStatsLiveData.value
                updateParts(parts = data.pages.map {
                    BiliVideoPartModel(
                        bvid = video.bvid,
                        cid = it.cid,
                        name = it.part,
                        remark = TimeUtils.formatSecondTime(seconds = it.duration)
                    )
                })
                mBiliUpDataLiveData.value = BiliUpData.from(owner = data.owner)
                mLoadingStatusLiveData.value = LoadingStatus.doneStatus()
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                mLoadingStatusLiveData.value = LoadingStatus.errorStatus(
                    message = message
                )
            }
        }
        NetworkManager.biliVideoRepository.requestVideoDetail(
            bvid = video.bvid,
            callback = callback
        )
    }

    /** 更新详情快照并保留仍有效的选择，先同步可选身份再发布列表。 */
    private fun updateParts(parts: List<BiliVideoPartModel>) {
        mPartSelectionLiveData.value = currentPartSelection().updateParts(parts = parts)
        mBiliVideoPageListLiveData.value = parts
    }

    /** 返回优先停止继续建任务，其次退出多选；已入队任务不受影响。 */
    fun onBack(): Boolean {
        if (mBatchProgressLiveData.value != null) {
            cancelBatchDownload()
            return true
        }
        if (currentPartSelection().enabled) {
            clearPartSelection()
            return true
        }
        return false
    }

    /** 多选时切换稳定分 P 身份，普通模式继续使用单项规格选择。 */
    fun onPartSelected(item: BiliVideoPartModel) {
        if (loadingVideoPartLiveData.value != null || mBatchProgressLiveData.value != null) return
        if (currentPartSelection().enabled) {
            mPartSelectionLiveData.value = currentPartSelection().toggle(part = item)
            mLatestChangeIndexLiveData.value = mBiliVideoPageListLiveData.value?.indexOf(item) ?: -1
            return
        }

        mSelectedVideoPartLiveData.value = item

        viewModelScope.launch {
            when (val result = loadPartDash(item = item)) {
                is ResultWrapper.Error -> popMessage(
                    message = ToastMessageAction(
                        content = result.error,
                        duration = Toast.LENGTH_SHORT
                    )
                )

                is ResultWrapper.Success -> onSelectPartLoaded(
                    part = item, dash = result.value
                )
            }
        }
    }

    /**
     * 用户选择的片段加载完成
     */
    private fun onSelectPartLoaded(
        part: BiliVideoPartModel,
        dash: BiliPlayStreamDash
    ) = viewModelScope.launch {
        val result = popSelectedVideoPartDialog(
            title = part.name, dash = dash
        ) as? ResultWrapper.Success ?: return@launch
        val taskCreated = startDownload(
            part = part,
            videoStream = result.value.videoStream,
            audioStream = result.value.audioStream
        )
        popMessage(
            message = ToastMessageAction(
                content = CommonLibs.getString(
                    id = if (taskCreated) R.string.text_added_download_queue
                    else R.string.text_download_task_already_active
                ),
                duration = Toast.LENGTH_SHORT
            )
        )
    }

    /** 长按进入多选并切换该项；重复长按不会意外关闭整栏。 */
    fun onItemLongClick(item: BiliVideoPartModel): Boolean {
        if (mBatchProgressLiveData.value != null || loadingVideoPartLiveData.value != null) return true
        mPartSelectionLiveData.value = currentPartSelection().toggle(part = item)
        mLatestChangeIndexLiveData.value = -1
        return true
    }

    /** 全选当前完整分 P 列表，或清空当前选择。 */
    fun onToggleSelectAllParts() {
        if (mBatchProgressLiveData.value != null) return
        mPartSelectionLiveData.value = currentPartSelection().toggleAll()
        mLatestChangeIndexLiveData.value = -1
    }

    /** 取消按钮在处理中停止后续建任务，空闲时退出多选。 */
    fun onCancelPartSelection() {
        if (mBatchProgressLiveData.value != null) cancelBatchDownload() else clearPartSelection()
    }

    /** 按详情原始顺序下载选中项，选择时的点击顺序不改变任务顺序。 */
    fun onDownloadMultipleSelectItems() {
        startPartBatch(
            parts = mBiliVideoPageListLiveData.value.orEmpty()
                .filter(currentPartSelection()::isSelected)
        )
    }

    /** 长按后可直接下载当前全部分 P，无需预先全选。 */
    fun onDownloadAllParts() {
        startPartBatch(parts = mBiliVideoPageListLiveData.value.orEmpty())
    }

    /** 固定本批次输入并防止重入，完成后统一发布用例的真实统计。 */
    private fun startPartBatch(parts: List<BiliVideoPartModel>) {
        if (parts.isEmpty() || mBatchProgressLiveData.value != null || loadingVideoPartLiveData.value != null) return
        val snapshot = parts.toList()
        mBatchProgressLiveData.value = BatchDownloadUseCase.Progress(
            processedCount = 0,
            total = snapshot.size,
            addedCount = 0,
            skippedCount = 0
        )
        mBatchDownloadJob = viewModelScope.launch {
            try {
                // 规格弹窗以可重放状态交给宿主，旋转不会丢失等待中的批次。
                val result = mBatchDownloadUseCase.executeParts(
                    parts = snapshot,
                    selectStreams = ::selectBatchStreams,
                    onProgress = { mBatchProgressLiveData.value = it },
                )
                if (result is BatchDownloadUseCase.Result.Completed) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(
                                id = R.string.text_batch_download_result,
                                args = arrayOf(
                                    result.addedCount,
                                    result.skippedCount
                                ),
                            ), duration = Toast.LENGTH_LONG
                        )
                    )
                    clearPartSelection()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                mBatchProgressLiveData.value = null
            }
        }
    }

    /** 取消只停止继续提交，明确告知已经加入的任务仍在下载队列中。 */
    private fun cancelBatchDownload() {
        val progress = mBatchProgressLiveData.value ?: return
        mBatchDownloadJob?.cancel()
        popMessage(
            message = ToastMessageAction(
                content = CommonLibs.getString(
                    id = R.string.text_batch_download_cancelled,
                    args = arrayOf(progress.addedCount),
                ), duration = Toast.LENGTH_LONG
            )
        )
    }

    /** 等待宿主选流；清理请求时同步通知宿主关闭失效弹窗。 */
    private suspend fun selectBatchStreams(
        request: BatchDownloadUseCase.StreamSelectionRequest,
    ): BatchDownloadResolver.StreamSelection? {
        val result = CompletableDeferred<BatchDownloadResolver.StreamSelection?>()
        mPendingBatchStreams = result
        mBatchStreamRequestLiveData.value =
            BatchStreamRequest(id = ++mNextBatchRequestId, request = request)
        return try {
            result.await()
        } finally {
            mPendingBatchStreams = null
            mBatchStreamRequestLiveData.value = null
        }
    }

    /** 仅接收当前规格请求的结果，取消初始选择会保留多选状态。 */
    fun onBatchStreamsSelected(requestId: Long, streams: BatchDownloadResolver.StreamSelection?) {
        if (mBatchStreamRequestLiveData.value?.id != requestId) return
        mPendingBatchStreams?.complete(streams)
    }

    private fun currentPartSelection(): VideoPartSelectionUiState =
        mPartSelectionLiveData.value ?: VideoPartSelectionUiState()

    /** 清空选择并通知复用中的分 P 行同步刷新。 */
    private fun clearPartSelection() {
        mPartSelectionLiveData.value = currentPartSelection().clear()
        mLatestChangeIndexLiveData.value = -1
    }

    /**
     * 加载片段dash
     */
    private suspend fun loadPartDash(item: BiliVideoPartModel) = suspendCancellableCoroutine { co ->
        mLoadingVideoPartLiveData.value = item
        val callback = object : IServerCallback<BiliPlayStreamDash> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliPlayStreamDash
            ) {
                mLoadingVideoPartLiveData.postValue(null)
                runCatching { co.resume(ResultWrapper.Success(value = data)) }
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                mLoadingVideoPartLiveData.postValue(null)
                runCatching { co.resume(ResultWrapper.Error(error = message)) }
            }
        }
        NetworkManager.biliVideoRepository.requestPlayStreamDash(
            bvid = item.bvid,
            cid = item.cid,
            callback = callback
        )
    }

    /**
     * 弹窗提示用户选择资源类型
     */
    private suspend fun popSelectedVideoPartDialog(
        title: String,
        dash: BiliPlayStreamDash
    ) = suspendCancellableCoroutine { co ->
        popDialog(
            dialog = BiliPartDialog.buildDialog(
                title = title, videoResources = dash.video, audioResources = dash.getAllAudio()
            ),
            success = {
                runCatching {
                    co.resume(ResultWrapper.Success(value = it as BiliPartDialog.Companion.Result))
                }
            },
            failed = {
                runCatching { co.resume(ResultWrapper.Error(error = it)) }
            }
        )
    }

    /**
     * 开始下载视频
     */
    private suspend fun startDownload(
        part: BiliVideoPartModel,
        videoStream: BiliPlayStreamResource?,
        audioStream: BiliPlayStreamResource?,
    ): Boolean {
        val resources = mutableListOf<BiliDashModel>().apply {
            videoStream?.let { res ->
                add(BiliDashModel.create(dashType = DashType.VIDEO, resource = res))
            }
            audioStream?.let { res ->
                add(BiliDashModel.create(dashType = DashType.AUDIO, resource = res))
            }
        }
        return DownloadManager.startDownload(
            context = CommonLibs.requireContext(),
            bvid = part.bvid,
            cid = part.cid,
            resources = resources
        )
    }

    fun onDescriptionLongClick(): Boolean {
        val resource = mBiliResourceModelLiveData.value ?: return false
        val isSuccess = CommonLibs.copyToClipboard(
            label = resource.title,
            text = CommonLibs.getString(
                id = R.string.video_details_format,
                args = arrayOf(
                    resource.title,
                    resource.description
                )
            )
        )
        if (isSuccess) {
            popMessage(message = ToastMessageAction(content = CommonLibs.getString(id = R.string.success_copy_video_info)))
        }
        return isSuccess
    }

    fun onClickUp() {
        if (AccountManager.accountLiveData.value == null) {
            popMessage(message = ToastMessageAction(content = CommonLibs.getString(id = R.string.please_login_to_your_account_first)))
            return
        }
        val mid = mBiliUpDataLiveData.value?.mid ?: return
        startActivity(
            targetClass = PersonalDetailsActivity::class.java,
            targetIntent = PersonalDetailsActivity.buildIntent(mid = mid)
        )
    }

    /**
     * 下载弹幕
     * @param part 视频片段
     */
    fun onDownloadDanmaku(part: BiliVideoPartModel) {
        if (AccountManager.accountLiveData.value == null) {
            popMessage(message = ToastMessageAction(content = CommonLibs.getString(id = R.string.please_login_to_your_account_first)))
            return
        }
        if (mDownloadingDanmakuPartLiveData.value != null) {
            popMessage(message = ToastMessageAction(content = CommonLibs.getString(id = R.string.danmaku_downloading_message)))
            return
        }
        // 发送确认对话框 ViewAction
        sendViewAction(action = ShowDownloadDanmakuConfirmAction(part = part))
    }

    /**
     * 确认下载弹幕（由 Activity 调用）
     * @param part 视频片段
     */
    fun confirmDownloadDanmaku(part: BiliVideoPartModel) {
        viewModelScope.launch {
            mDownloadingDanmakuPartLiveData.value = part

            try {
                // 请求弹幕数据
                when (val result = requestDanmakuData(part = part)) {
                    is ResultWrapper.Error -> {
                        popMessage(
                            message = ToastMessageAction(
                                content = result.error,
                                duration = Toast.LENGTH_SHORT
                            )
                        )
                    }

                    is ResultWrapper.Success -> {
                        exportDanmakuToCsv(part = part, danmakuList = result.value)
                    }
                }
            } finally {
                mDownloadingDanmakuPartLiveData.value = null
            }
        }
    }

    /**
     * 请求弹幕数据
     * 根据登录状态自动选择合适的API：
     * - 未登录：使用实时弹幕API（最多600条）
     * - 已登录：使用分段弹幕API（获取更多弹幕）
     */
    private suspend fun requestDanmakuData(part: BiliVideoPartModel) =
        suspendCancellableCoroutine { co ->
            val isLoggedIn = AccountManager.accountLiveData.value != null
            val callback = object : IServerCallback<List<BiliXmlDanmaku>> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: List<BiliXmlDanmaku>
                ) {
                    runCatching {
                        co.resume(ResultWrapper.Success(value = data))
                    }
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    runCatching {
                        co.resume(
                            ResultWrapper.Error(
                                error = CommonLibs.getString(
                                    id = R.string.danmaku_fetch_failed_message,
                                    args = arrayOf(message)
                                )
                            )
                        )
                    }
                }
            }

            // 根据登录状态选择不同的API
            if (isLoggedIn) {
                // 已登录：使用全量弹幕下载
                NetworkManager.biliVideoRepository.requestFullDanmaku(
                    cid = part.cid,
                    callback = callback
                )
            } else {
                // 未登录：使用实时弹幕API（最多600条）
                NetworkManager.biliVideoRepository.requestDanmakuXmlData(
                    cid = part.cid,
                    callback = callback
                )
            }
        }

    /**
     * 导出弹幕到CSV文件
     */
    private fun exportDanmakuToCsv(part: BiliVideoPartModel, danmakuList: List<BiliXmlDanmaku>) {
        if (danmakuList.isEmpty()) {
            popMessage(
                message = ToastMessageAction(
                    content = CommonLibs.getString(id = R.string.danmaku_no_danmaku_message),
                    duration = Toast.LENGTH_SHORT
                )
            )
            return
        }
        try {
            // 发送ViewAction到Activity，让用户选择保存路径
            sendViewAction(action = ExportDanmakuAction(danmakuList = danmakuList))
        } catch (e: Exception) {
            e.printStackTrace()
            popMessage(
                message = ToastMessageAction(
                    content = CommonLibs.getString(
                        id = R.string.danmaku_export_exception_message,
                        args = arrayOf(e.message ?: "")
                    ), duration = Toast.LENGTH_SHORT
                )
            )
        }
    }

    /**
     * 将弹幕导出到用户选择的URI
     */
    fun exportDanmakuToUri(uri: Uri, danmakuList: List<BiliXmlDanmaku>) {
        viewModelScope.launch {
            try {
                val context = CommonLibs.requireContext()
                val success = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    DanmakuExportUtils.exportToCsv(
                        danmakuList = danmakuList,
                        outputStream = outputStream
                    )
                } ?: false

                if (success) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.danmaku_export_success_message),
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                } else {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.danmaku_export_failed_message),
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                popMessage(
                    message = ToastMessageAction(
                        content = CommonLibs.getString(
                            id = R.string.danmaku_export_exception_message,
                            args = arrayOf(e.message ?: "")
                        ), duration = Toast.LENGTH_SHORT
                    )
                )
            }
        }
    }

    /**
     * 下载字幕
     * @param part 视频片段
     */
    fun onDownloadSubtitle(part: BiliVideoPartModel) {
        if (AccountManager.accountLiveData.value == null) {
            popMessage(message = ToastMessageAction(content = CommonLibs.getString(id = R.string.please_login_to_your_account_first)))
            return
        }
        if (mDownloadingSubtitlePartLiveData.value != null) {
            popMessage(message = ToastMessageAction(content = CommonLibs.getString(id = R.string.subtitle_downloading_message)))
            return
        }
        sendViewAction(action = ShowDownloadSubtitleConfirmAction(part = part))
    }

    /**
     * 确认下载字幕（由 Activity 调用）
     */
    fun confirmDownloadSubtitle(part: BiliVideoPartModel) {
        viewModelScope.launch {
            mDownloadingSubtitlePartLiveData.value = part
            try {
                // 1. 请求字幕列表
                val container = requestSubtitleList(part = part)
                if (container is ResultWrapper.Error) {
                    popMessage(
                        message = ToastMessageAction(
                            content = container.error,
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                    return@launch
                }

                val listData = (container as ResultWrapper.Success).value
                val subtitles = listData.data?.subtitle?.subtitles
                if (subtitles.isNullOrEmpty()) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.subtitle_no_subtitle_message),
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                    return@launch
                }

                // 优先选择中文(zh-CN)，其次选第一个
                val targetSubtitle = subtitles.find { it.lan == "zh-CN" } ?: subtitles.first()
                val subtitleUrl = targetSubtitle.subtitleUrl

                // 2. 请求BCC字幕数据
                when (val result = requestBccSubtitleData(url = subtitleUrl)) {
                    is ResultWrapper.Error -> {
                        popMessage(
                            message = ToastMessageAction(
                                content = result.error,
                                duration = Toast.LENGTH_SHORT
                            )
                        )
                    }

                    is ResultWrapper.Success -> {
                        val bccSubtitle = result.value
                        if (bccSubtitle.body.isNullOrEmpty()) {
                            popMessage(
                                message = ToastMessageAction(
                                    content = CommonLibs.getString(id = R.string.subtitle_no_subtitle_message),
                                    duration = Toast.LENGTH_SHORT
                                )
                            )
                        } else {
                            sendViewAction(action = ExportSubtitleAction(bccSubtitle = bccSubtitle))
                        }
                    }
                }
            } finally {
                mDownloadingSubtitlePartLiveData.value = null
            }
        }
    }

    private suspend fun requestSubtitleList(part: BiliVideoPartModel) =
        suspendCancellableCoroutine { co ->
            val callback = object : IServerCallback<BiliSubtitleListContainer> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: BiliSubtitleListContainer
                ) {
                    runCatching { co.resume(ResultWrapper.Success(value = data)) }
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    runCatching {
                        co.resume(
                            ResultWrapper.Error(
                                error = CommonLibs.getString(
                                    id = R.string.subtitle_fetch_failed_message,
                                    args = arrayOf(message)
                                )
                            )
                        )
                    }
                }
            }
            NetworkManager.biliVideoRepository.requestSubtitleList(
                cid = part.cid,
                bvid = part.bvid,
                callback = callback
            )
        }

    private suspend fun requestBccSubtitleData(url: String) = suspendCancellableCoroutine { co ->
        val callback = object : IServerCallback<BccSubtitle> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BccSubtitle
            ) {
                runCatching { co.resume(ResultWrapper.Success(value = data)) }
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                runCatching {
                    co.resume(
                        ResultWrapper.Error(
                            error = CommonLibs.getString(
                                id = R.string.subtitle_fetch_failed_message,
                                args = arrayOf(message)
                            )
                        )
                    )
                }
            }
        }
        NetworkManager.biliVideoRepository.requestSubtitleData(url = url, callback = callback)
    }

    /**
     * 将字幕导出到用户选择的URI
     */
    fun exportSubtitleToUri(uri: Uri, bccSubtitle: BccSubtitle) {
        viewModelScope.launch {
            try {
                val context = CommonLibs.requireContext()
                val success = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    SubtitleExportUtils.exportToSrt(bcc = bccSubtitle, outputStream = outputStream)
                    true
                } ?: false

                if (success) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.subtitle_export_success_message),
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                } else {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.subtitle_export_failed_message),
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                popMessage(
                    message = ToastMessageAction(
                        content = CommonLibs.getString(
                            id = R.string.subtitle_export_exception_message,
                            args = arrayOf(e.message ?: "")
                        ), duration = Toast.LENGTH_SHORT
                    )
                )
            }
        }
    }

    /**
     * 生成图文笔记（新增功能）：点击分P上的笔记按钮后先弹确认框。
     * @param part 视频片段
     */
    fun onGenerateNotes(part: BiliVideoPartModel) {
        if (AccountManager.accountLiveData.value == null) {
            popMessage(message = ToastMessageAction(content = CommonLibs.getString(id = R.string.please_login_to_your_account_first)))
            return
        }
        if (mGeneratingNotesPartLiveData.value != null) {
            popMessage(message = ToastMessageAction(content = CommonLibs.getString(id = R.string.notes_generating_message)))
            return
        }
        sendViewAction(action = ShowGenerateNotesConfirmAction(part = part))
    }

    /**
     * 确认生成图文笔记（由 Activity 调用）：
     * 取字幕（优先 zh-CN）→ 下载 720P 视频流到缓存 → 按字幕截图排版生成 PDF → 交 Activity 导出。
     */
    fun confirmGenerateNotes(part: BiliVideoPartModel) {
        viewModelScope.launch {
            mGeneratingNotesPartLiveData.value = part
            val context = CommonLibs.requireContext()
            val videoFile = File(context.cacheDir, "notes_${part.bvid}_${part.cid}.mp4")
            try {
                // 1. 请求字幕列表并选定字幕（与下载字幕相同的规则）
                val container = requestSubtitleList(part = part)
                if (container is ResultWrapper.Error) {
                    popMessage(
                        message = ToastMessageAction(
                            content = container.error,
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                    return@launch
                }
                val subtitles = (container as ResultWrapper.Success).value.data?.subtitle?.subtitles
                if (subtitles.isNullOrEmpty()) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.subtitle_no_subtitle_message),
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                    return@launch
                }
                val targetSubtitle = subtitles.find { it.lan == "zh-CN" } ?: subtitles.first()
                val bccSubtitle = when (val result = requestBccSubtitleData(url = targetSubtitle.subtitleUrl)) {
                    is ResultWrapper.Error -> {
                        popMessage(
                            message = ToastMessageAction(
                                content = result.error,
                                duration = Toast.LENGTH_SHORT
                            )
                        )
                        return@launch
                    }

                    is ResultWrapper.Success -> result.value
                }
                if (bccSubtitle.body.isNullOrEmpty()) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.subtitle_no_subtitle_message),
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                    return@launch
                }

                // 2. 取 DASH 流并挑一条 720P 视频流（只用来截图，生成后即删）
                val dash = when (val result = loadPartDash(item = part)) {
                    is ResultWrapper.Error -> {
                        popMessage(
                            message = ToastMessageAction(
                                content = result.error,
                                duration = Toast.LENGTH_SHORT
                            )
                        )
                        return@launch
                    }

                    is ResultWrapper.Success -> result.value
                }
                val videoStream = selectNotesVideoStream(dash = dash)
                if (videoStream == null) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(
                                id = R.string.notes_generate_failed_detail_message,
                                args = arrayOf("no video stream")
                            ),
                            duration = Toast.LENGTH_SHORT
                        )
                    )
                    return@launch
                }

                // 3. 下载 720P 流并生成 PDF（耗时操作，切 IO 线程）
                var durationMismatch = false
                var downloadError: String? = null
                val pdfFile = withContext(Dispatchers.IO) {
                    val error = downloadStreamToFile(
                        urls = videoStream.getStreamUrls(),
                        outFile = videoFile
                    )
                    if (error != null) {
                        downloadError = error
                        return@withContext null
                    }
                    val outFile = File(
                        context.cacheDir,
                        DownloadFileNameUtils.sanitizeFileName(fileName = "${part.name}.pdf")
                    )
                    val result = NotesPdfGenerator.generate(
                        context = context,
                        title = part.name,
                        subtitle = bccSubtitle,
                        videoFile = videoFile,
                        outFile = outFile
                    )
                    durationMismatch = result.durationMismatch
                    outFile
                }
                if (pdfFile == null) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(
                                id = R.string.notes_generate_failed_detail_message,
                                args = arrayOf("download: ${downloadError ?: "unknown"}")
                            ),
                            duration = Toast.LENGTH_LONG
                        )
                    )
                    return@launch
                }
                if (durationMismatch) {
                    popMessage(
                        message = ToastMessageAction(
                            content = CommonLibs.getString(id = R.string.notes_duration_mismatch_message),
                            duration = Toast.LENGTH_LONG
                        )
                    )
                }
                sendViewAction(action = ExportNotesAction(pdfFile = pdfFile, fileName = pdfFile.name))
            } catch (e: Exception) {
                e.printStackTrace()
                popMessage(
                    message = ToastMessageAction(
                        content = CommonLibs.getString(
                            id = R.string.notes_generate_exception_message,
                            args = arrayOf(e.message ?: "")
                        ), duration = Toast.LENGTH_SHORT
                    )
                )
            } finally {
                mGeneratingNotesPartLiveData.value = null
                videoFile.delete()
            }
        }
    }

    /**
     * 为生成笔记挑流，按推荐程度排序（截图用途：稳和清楚优先于省流量）：
     * 1. 清晰度：优先向上选——有 720P 或更高的，选其中最低的一档（720P 本身最优先，
     *    其次 900P/1080P 等，避免动辄 4K）；全部低于 720P 时才选最高的一档；
     * 2. 编码：AVC > HEVC > AV1（AVC 解码最稳最快，HEVC 次之，AV1 最吃性能）；
     * 3. 同清晰度同编码时选码率更高的一条，截图里的文字更清楚。
     */
    private fun selectNotesVideoStream(dash: BiliPlayStreamDash): BiliPlayStreamResource? {
        val videos = dash.video.orEmpty()
        if (videos.isEmpty()) return null
        fun codecRank(resource: BiliPlayStreamResource): Int = when {
            resource.codecs.startsWith("avc1") || resource.codecId == 7L -> 0
            resource.codecs.startsWith("hev1") || resource.codecs.startsWith("hvc1") ||
                resource.codecId == 12L -> 1

            resource.codecs.startsWith("av01") || resource.codecId == 13L -> 2
            else -> 3
        }
        val comparator = compareBy<BiliPlayStreamResource>(
            // 先分档：>=720 的一档优先于 <720 的一档
            { if (it.height >= 720) 0 else 1 },
            // 档内排序：>=720 档选高度最小的（最接近 720 的更高清），<720 档选高度最大的（最清楚的一档）
            { if (it.height >= 720) it.height else -it.height },
            { codecRank(resource = it) }
        ).thenByDescending { it.bandwidth }
        return videos.minWithOrNull(comparator)
    }

    /**
     * 把视频流下载到本地文件：候选地址（主地址+备用地址）逐个尝试，
     * 请求头与 Cookie 和 App 正常下载视频时完全一致（见 DownloadManager.buildRequest），
     * 避免被 B 站 CDN 拒绝。阻塞方法，须在 IO 线程调用。
     *
     * @return 成功返回 null，失败返回失败原因（HTTP 状态码或异常信息），用于提示与排查。
     */
    private fun downloadStreamToFile(urls: List<String>, outFile: File): String? {
        var lastError = "no stream url"
        for (url in urls) {
            val error: String? = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        NetworkConfig.DOWNLOAD_HEADERS.forEach { (key, value) ->
                            header(key, value)
                        }
                        AccountManager.cookiesLiveData.value?.let { header("Cookie", it) }
                    }
                    .build()
                NetworkManager.downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use "HTTP ${response.code()}"
                    val body = response.body() ?: return@use "empty response"
                    outFile.outputStream().use { output -> body.byteStream().copyTo(output) }
                    if (outFile.length() > 0) null else "empty file"
                }
            }.getOrElse { it.message ?: it.javaClass.simpleName }
            if (error == null) return null
            lastError = error
        }
        return lastError
    }

    /**
     * 将生成的笔记 PDF 导出到用户选择的 URI，导出后删除缓存文件。
     */
    fun exportNotesToUri(uri: Uri, pdfFile: File) {
        viewModelScope.launch {
            try {
                val context = CommonLibs.requireContext()
                val success = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        pdfFile.inputStream().use { input -> input.copyTo(outputStream) }
                        true
                    } ?: false
                }
                popMessage(
                    message = ToastMessageAction(
                        content = CommonLibs.getString(
                            id = if (success) {
                                R.string.notes_export_success_message
                            } else {
                                R.string.notes_export_failed_message
                            }
                        ),
                        duration = Toast.LENGTH_SHORT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                popMessage(
                    message = ToastMessageAction(
                        content = CommonLibs.getString(
                            id = R.string.notes_export_exception_message,
                            args = arrayOf(e.message ?: "")
                        ), duration = Toast.LENGTH_SHORT
                    )
                )
            } finally {
                pdfFile.delete()
            }
        }
    }

    /**
     * 封面图片点击事件
     */
    fun onCoverClick() {
        val resource = mBiliResourceModelLiveData.value ?: return
        val coverUrl = resource.cover
        val bvid = when (resource) {
            is BiliVideoModel -> resource.bvid
            else -> null
        }
        sendViewAction(action = ShowSaveCoverConfirmAction(coverUrl = coverUrl, bvid = bvid))
    }

    /**
     * 保存封面图片
     */
    private fun onSaveCover(coverUrl: String, bvid: String?) {
        // 从URL中提取扩展名，默认为jpg
        val extension = when {
            coverUrl.contains(".jpg", ignoreCase = true) || coverUrl.contains(
                ".jpeg",
                ignoreCase = true
            ) -> ".jpg"

            coverUrl.contains(".png", ignoreCase = true) -> ".png"
            coverUrl.contains(".webp", ignoreCase = true) -> ".webp"
            coverUrl.contains(".gif", ignoreCase = true) -> ".gif"
            else -> ".jpg"
        }

        // 使用 bv 号作为默认文件名；缺失时按标题生成，仍须清理非法字符后才能创建文档。
        val fileName = if (bvid != null) {
            "${bvid}$extension"
        } else {
            // 如果没有 bvid，使用标题
            val resource = mBiliResourceModelLiveData.value ?: return
            DownloadFileNameUtils.sanitizeFileName(fileName = "${resource.title.limit(maxLength = 100)}$extension")
        }

        sendViewAction(action = SaveCoverAction(coverUrl = coverUrl, fileName = fileName))
    }

    /**
     * 确认保存封面（由 Activity 调用）
     */
    fun confirmSaveCover(coverUrl: String, bvid: String?) {
        onSaveCover(coverUrl = coverUrl, bvid = bvid)
    }
}
