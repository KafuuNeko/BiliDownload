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
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliXmlDanmaku
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.model.BiliSubtitleListContainer
import cc.kafuu.bilidownload.common.network.model.BccSubtitle
import cc.kafuu.bilidownload.common.utils.DanmakuExportUtils
import cc.kafuu.bilidownload.common.utils.SubtitleExportUtils
import cc.kafuu.bilidownload.common.utils.TimeUtils
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.PersonalDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.BiliPartDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
    data class BatchStreamRequest(val id: Long, val request: BatchDownloadUseCase.StreamSelectionRequest)

    private var mNextBatchRequestId = 0L
    private var mPendingBatchStreams: CompletableDeferred<BatchDownloadResolver.StreamSelection?>? = null
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
                updateParts(data.episodes.map {
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
                media.seasonId,
                callback
            )
        } else {
            NetworkManager.biliVideoRepository.requestSeasonDetailByEpId(media.mediaId, callback)
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
                    ?.withFallback(mVideoStatsLiveData.value) ?: mVideoStatsLiveData.value
                updateParts(data.pages.map {
                    BiliVideoPartModel(
                        bvid = video.bvid,
                        cid = it.cid,
                        name = it.part,
                        remark = TimeUtils.formatSecondTime(it.duration)
                    )
                })
                mBiliUpDataLiveData.value = BiliUpData.from(data.owner)
                mLoadingStatusLiveData.value = LoadingStatus.doneStatus()
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                mLoadingStatusLiveData.value = LoadingStatus.errorStatus(
                    message = message
                )
            }
        }
        NetworkManager.biliVideoRepository.requestVideoDetail(video.bvid, callback)
    }

    /** 更新详情快照并保留仍有效的选择，先同步可选身份再发布列表。 */
    private fun updateParts(parts: List<BiliVideoPartModel>) {
        mPartSelectionLiveData.value = currentPartSelection().updateParts(parts)
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
            mPartSelectionLiveData.value = currentPartSelection().toggle(item)
            mLatestChangeIndexLiveData.value = mBiliVideoPageListLiveData.value?.indexOf(item) ?: -1
            return
        }

        mSelectedVideoPartLiveData.value = item

        viewModelScope.launch {
            when (val result = loadPartDash(item)) {
                is ResultWrapper.Error -> popMessage(
                    ToastMessageAction(result.error, Toast.LENGTH_SHORT)
                )

                is ResultWrapper.Success -> onSelectPartLoaded(
                    item, result.value
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
            part.name, dash
        ) as? ResultWrapper.Success ?: return@launch
        val taskCreated = startDownload(
            part,
            result.value.videoStream,
            result.value.audioStream
        )
        popMessage(
            ToastMessageAction(
                CommonLibs.getString(
                    if (taskCreated) R.string.text_added_download_queue
                    else R.string.text_download_task_already_active
                ),
                Toast.LENGTH_SHORT
            )
        )
    }

    /** 长按进入多选并切换该项；重复长按不会意外关闭整栏。 */
    fun onItemLongClick(item: BiliVideoPartModel): Boolean {
        if (mBatchProgressLiveData.value != null || loadingVideoPartLiveData.value != null) return true
        mPartSelectionLiveData.value = currentPartSelection().toggle(item)
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
        startPartBatch(mBiliVideoPageListLiveData.value.orEmpty().filter(currentPartSelection()::isSelected))
    }

    /** 长按后可直接下载当前全部分 P，无需预先全选。 */
    fun onDownloadAllParts() {
        startPartBatch(mBiliVideoPageListLiveData.value.orEmpty())
    }

    /** 固定本批次输入并防止重入，完成后统一发布用例的真实统计。 */
    private fun startPartBatch(parts: List<BiliVideoPartModel>) {
        if (parts.isEmpty() || mBatchProgressLiveData.value != null || loadingVideoPartLiveData.value != null) return
        val snapshot = parts.toList()
        mBatchProgressLiveData.value = BatchDownloadUseCase.Progress(0, snapshot.size, 0, 0)
        mBatchDownloadJob = viewModelScope.launch {
            try {
                // 规格弹窗以可重放状态交给宿主，旋转不会丢失等待中的批次。
                val result = mBatchDownloadUseCase.executeParts(
                    parts = snapshot,
                    selectStreams = ::selectBatchStreams,
                    onProgress = { mBatchProgressLiveData.value = it },
                )
                if (result is BatchDownloadUseCase.Result.Completed) {
                    popMessage(ToastMessageAction(CommonLibs.getString(
                        R.string.text_batch_download_result, result.addedCount, result.skippedCount,
                    ), Toast.LENGTH_LONG))
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
        popMessage(ToastMessageAction(CommonLibs.getString(
            R.string.text_batch_download_cancelled, progress.addedCount,
        ), Toast.LENGTH_LONG))
    }

    /** 等待宿主选流；清理请求时同步通知宿主关闭失效弹窗。 */
    private suspend fun selectBatchStreams(
        request: BatchDownloadUseCase.StreamSelectionRequest,
    ): BatchDownloadResolver.StreamSelection? {
        val result = CompletableDeferred<BatchDownloadResolver.StreamSelection?>()
        mPendingBatchStreams = result
        mBatchStreamRequestLiveData.value = BatchStreamRequest(++mNextBatchRequestId, request)
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
                runCatching { co.resume(ResultWrapper.Success(data)) }
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                mLoadingVideoPartLiveData.postValue(null)
                runCatching { co.resume(ResultWrapper.Error(message)) }
            }
        }
        NetworkManager.biliVideoRepository.requestPlayStreamDash(item.bvid, item.cid, callback)
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
                title, dash.video, dash.getAllAudio()
            ),
            success = {
                runCatching {
                    co.resume(ResultWrapper.Success(it as BiliPartDialog.Companion.Result))
                }
            },
            failed = {
                runCatching { co.resume(ResultWrapper.Error(it)) }
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
                add(BiliDashModel.create(DashType.VIDEO, res))
            }
            audioStream?.let { res ->
                add(BiliDashModel.create(DashType.AUDIO, res))
            }
        }
        return DownloadManager.startDownload(
            CommonLibs.requireContext(),
            part.bvid,
            part.cid,
            resources
        )
    }

    fun onDescriptionLongClick(): Boolean {
        val resource = mBiliResourceModelLiveData.value ?: return false
        val isSuccess = CommonLibs.copyToClipboard(
            label = resource.title,
            text = CommonLibs.getString(
                R.string.video_details_format,
                resource.title,
                resource.description
            )
        )
        if (isSuccess) {
            popMessage(ToastMessageAction(CommonLibs.getString(R.string.success_copy_video_info)))
        }
        return isSuccess
    }

    fun onClickUp() {
        if (AccountManager.accountLiveData.value == null) {
            popMessage(ToastMessageAction(CommonLibs.getString(R.string.please_login_to_your_account_first)))
            return
        }
        val mid = mBiliUpDataLiveData.value?.mid ?: return
        startActivity(
            PersonalDetailsActivity::class.java,
            PersonalDetailsActivity.buildIntent(mid)
        )
    }

    /**
     * 下载弹幕
     * @param part 视频片段
     */
    fun onDownloadDanmaku(part: BiliVideoPartModel) {
        if (AccountManager.accountLiveData.value == null) {
            popMessage(ToastMessageAction(CommonLibs.getString(R.string.please_login_to_your_account_first)))
            return
        }
        if (mDownloadingDanmakuPartLiveData.value != null) {
            popMessage(ToastMessageAction(CommonLibs.getString(R.string.danmaku_downloading_message)))
            return
        }
        // 发送确认对话框 ViewAction
        sendViewAction(ShowDownloadDanmakuConfirmAction(part))
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
                when (val result = requestDanmakuData(part)) {
                    is ResultWrapper.Error -> {
                        popMessage(ToastMessageAction(result.error, Toast.LENGTH_SHORT))
                    }

                    is ResultWrapper.Success -> {
                        exportDanmakuToCsv(part, result.value)
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
                        co.resume(ResultWrapper.Success(data))
                    }
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    runCatching {
                        co.resume(
                            ResultWrapper.Error(
                                CommonLibs.getString(
                                    R.string.danmaku_fetch_failed_message,
                                    message
                                )
                            )
                        )
                    }
                }
            }

            // 根据登录状态选择不同的API
            if (isLoggedIn) {
                // 已登录：使用全量弹幕下载
                NetworkManager.biliVideoRepository.requestFullDanmaku(part.cid, callback)
            } else {
                // 未登录：使用实时弹幕API（最多600条）
                NetworkManager.biliVideoRepository.requestDanmakuXmlData(part.cid, callback)
            }
        }

    /**
     * 导出弹幕到CSV文件
     */
    private fun exportDanmakuToCsv(part: BiliVideoPartModel, danmakuList: List<BiliXmlDanmaku>) {
        if (danmakuList.isEmpty()) {
            popMessage(
                ToastMessageAction(
                    CommonLibs.getString(R.string.danmaku_no_danmaku_message),
                    Toast.LENGTH_SHORT
                )
            )
            return
        }
        try {
            // 发送ViewAction到Activity，让用户选择保存路径
            sendViewAction(ExportDanmakuAction(danmakuList))
        } catch (e: Exception) {
            e.printStackTrace()
            popMessage(
                ToastMessageAction(
                    CommonLibs.getString(
                        R.string.danmaku_export_exception_message,
                        e.message ?: ""
                    ), Toast.LENGTH_SHORT
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
                    DanmakuExportUtils.exportToCsv(danmakuList, outputStream)
                } ?: false

                if (success) {
                    popMessage(
                        ToastMessageAction(
                            CommonLibs.getString(R.string.danmaku_export_success_message),
                            Toast.LENGTH_SHORT
                        )
                    )
                } else {
                    popMessage(
                        ToastMessageAction(
                            CommonLibs.getString(R.string.danmaku_export_failed_message),
                            Toast.LENGTH_SHORT
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                popMessage(
                    ToastMessageAction(
                        CommonLibs.getString(
                            R.string.danmaku_export_exception_message,
                            e.message ?: ""
                        ), Toast.LENGTH_SHORT
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
            popMessage(ToastMessageAction(CommonLibs.getString(R.string.please_login_to_your_account_first)))
            return
        }
        if (mDownloadingSubtitlePartLiveData.value != null) {
            popMessage(ToastMessageAction(CommonLibs.getString(R.string.subtitle_downloading_message)))
            return
        }
        sendViewAction(ShowDownloadSubtitleConfirmAction(part))
    }

    /**
     * 确认下载字幕（由 Activity 调用）
     */
    fun confirmDownloadSubtitle(part: BiliVideoPartModel) {
        viewModelScope.launch {
            mDownloadingSubtitlePartLiveData.value = part
            try {
                // 1. 请求字幕列表
                val container = requestSubtitleList(part)
                if (container is ResultWrapper.Error) {
                    popMessage(ToastMessageAction(container.error, Toast.LENGTH_SHORT))
                    return@launch
                }

                val listData = (container as ResultWrapper.Success).value
                val subtitles = listData.data?.subtitle?.subtitles
                if (subtitles.isNullOrEmpty()) {
                    popMessage(ToastMessageAction(CommonLibs.getString(R.string.subtitle_no_subtitle_message), Toast.LENGTH_SHORT))
                    return@launch
                }
                
                // 优先选择中文(zh-CN)，其次选第一个
                val targetSubtitle = subtitles.find { it.lan == "zh-CN" } ?: subtitles.first()
                val subtitleUrl = targetSubtitle.subtitleUrl
                
                // 2. 请求BCC字幕数据
                when (val result = requestBccSubtitleData(subtitleUrl)) {
                    is ResultWrapper.Error -> {
                        popMessage(ToastMessageAction(result.error, Toast.LENGTH_SHORT))
                    }
                    is ResultWrapper.Success -> {
                        val bccSubtitle = result.value
                        if (bccSubtitle.body.isNullOrEmpty()) {
                            popMessage(ToastMessageAction(CommonLibs.getString(R.string.subtitle_no_subtitle_message), Toast.LENGTH_SHORT))
                        } else {
                            sendViewAction(ExportSubtitleAction(bccSubtitle))
                        }
                    }
                }
            } finally {
                mDownloadingSubtitlePartLiveData.value = null
            }
        }
    }

    private suspend fun requestSubtitleList(part: BiliVideoPartModel) = suspendCancellableCoroutine { co ->
        val callback = object : IServerCallback<BiliSubtitleListContainer> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliSubtitleListContainer
            ) {
                runCatching { co.resume(ResultWrapper.Success(data)) }
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                runCatching {
                    co.resume(ResultWrapper.Error(CommonLibs.getString(R.string.subtitle_fetch_failed_message, message)))
                }
            }
        }
        NetworkManager.biliVideoRepository.requestSubtitleList(part.cid, part.bvid, callback)
    }

    private suspend fun requestBccSubtitleData(url: String) = suspendCancellableCoroutine { co ->
        val callback = object : IServerCallback<BccSubtitle> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BccSubtitle
            ) {
                runCatching { co.resume(ResultWrapper.Success(data)) }
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                runCatching {
                    co.resume(ResultWrapper.Error(CommonLibs.getString(R.string.subtitle_fetch_failed_message, message)))
                }
            }
        }
        NetworkManager.biliVideoRepository.requestSubtitleData(url, callback)
    }

    /**
     * 将字幕导出到用户选择的URI
     */
    fun exportSubtitleToUri(uri: Uri, bccSubtitle: BccSubtitle) {
        viewModelScope.launch {
            try {
                val context = CommonLibs.requireContext()
                val success = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    SubtitleExportUtils.exportToSrt(bccSubtitle, outputStream)
                    true
                } ?: false

                if (success) {
                    popMessage(
                        ToastMessageAction(
                            CommonLibs.getString(R.string.subtitle_export_success_message),
                            Toast.LENGTH_SHORT
                        )
                    )
                } else {
                    popMessage(
                        ToastMessageAction(
                            CommonLibs.getString(R.string.subtitle_export_failed_message),
                            Toast.LENGTH_SHORT
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                popMessage(
                    ToastMessageAction(
                        CommonLibs.getString(
                            R.string.subtitle_export_exception_message,
                            e.message ?: ""
                        ), Toast.LENGTH_SHORT
                    )
                )
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
        sendViewAction(ShowSaveCoverConfirmAction(coverUrl, bvid))
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

        // 使用 bv 号作为默认文件名
        val fileName = if (bvid != null) {
            "${bvid}$extension"
        } else {
            // 如果没有 bvid，使用标题
            val resource = mBiliResourceModelLiveData.value ?: return
            "${resource.title.limit(100)}$extension"
        }

        sendViewAction(SaveCoverAction(coverUrl, fileName))
    }

    /**
     * 确认保存封面（由 Activity 调用）
     */
    fun confirmSaveCover(coverUrl: String, bvid: String?) {
        onSaveCover(coverUrl, bvid)
    }
}
