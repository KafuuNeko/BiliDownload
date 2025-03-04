package cc.kafuu.bilidownload.viewmodel.activity

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliResourceModel
import cc.kafuu.bilidownload.common.model.bili.BiliUpData
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.utils.TimeUtils
import cc.kafuu.bilidownload.view.activity.PersonalDetailsActivity
import cc.kafuu.bilidownload.view.dialog.BiliPartDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class VideoDetailsViewModel : CoreViewModel() {
    private val mLoadingStatusLiveData = MutableLiveData(LoadingStatus.waitStatus())
    val loadingStatusLiveData = mLoadingStatusLiveData.liveData()

    private val mBiliResourceModelLiveData = MutableLiveData<BiliResourceModel>()
    val biliResourceModelLiveData = mBiliResourceModelLiveData.liveData()

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

    // 是否处于多选模式
    private val mMultipleSelectModeLiveData = MutableLiveData(false)
    val multipleSelectModeLiveData = mMultipleSelectModeLiveData.liveData()

    // 多选模式下选中的项目
    private val mMultipleSelectItemsLiveData = MutableLiveData<Set<BiliVideoPartModel>>()
    val multipleSelectItemsLiveData = mMultipleSelectItemsLiveData.liveData()

    // 最近被改变状态的列表索引
    private val mLatestChangeIndexLiveData = MutableLiveData(-1)
    val latestChangeIndexLiveData = mLatestChangeIndexLiveData.liveData()

    fun initData(media: BiliMediaModel) {
        mLoadingStatusLiveData.value = LoadingStatus.loadingStatus()
        mBiliResourceModelLiveData.value = media

        val callback = object : IServerCallback<BiliSeasonData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliSeasonData
            ) {
                mBiliVideoPageListLiveData.postValue(data.episodes.map {
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

    fun initData(video: BiliVideoModel) {
        mLoadingStatusLiveData.value = LoadingStatus.loadingStatus()
        mBiliResourceModelLiveData.value = video
        val callback = object : IServerCallback<BiliVideoData> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: BiliVideoData) {
                mBiliVideoPageListLiveData.postValue(data.pages.map {
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

    fun onBack(): Boolean {
        if (mMultipleSelectModeLiveData.value == true) {
            onSwitchMultipleSelectMode()
            return true
        }
        return false
    }

    @Synchronized
    fun onPartSelected(item: BiliVideoPartModel) {
        if (loadingVideoPartLiveData.value != null) return

        if (mMultipleSelectModeLiveData.value == true) {
            // 多选模式操作
            (mMultipleSelectItemsLiveData.value?.toMutableSet() ?: mutableSetOf()).apply {
                if (contains(item)) remove(item) else add(item)
            }.also {
                mMultipleSelectItemsLiveData.value = it
            }
            mLatestChangeIndexLiveData.value = mBiliVideoPageListLiveData.value?.indexOf(item)
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
        startDownload(part, result.value.videoStream, result.value.audioStream)
    }

    /**
     * 用户长按表项
     */
    fun onItemLongClick(item: BiliVideoPartModel): Boolean {
        onSwitchMultipleSelectMode()
        if (mMultipleSelectModeLiveData.value == true) {
            onPartSelected(item)
        }
        return true
    }

    /**
     * 切换多选模式
     */
    fun onSwitchMultipleSelectMode() {
        mMultipleSelectModeLiveData.value = !(mMultipleSelectModeLiveData.value ?: false)
        mMultipleSelectItemsLiveData.value = emptySet()
        mLatestChangeIndexLiveData.value = -1
    }

    /**
     * 下载多选选中的项目
     */
    fun onDownloadMultipleSelectItems() = viewModelScope.launch {
        val partList = mMultipleSelectItemsLiveData.value?.toList() ?: return@launch
        if (partList.isEmpty()) return@launch
        // 请求所有选中的片段dash
        val dashList = partList.map {
            when (val result = loadPartDash(it)) {
                is ResultWrapper.Error -> {
                    popMessage(ToastMessageAction(result.error, Toast.LENGTH_SHORT))
                    return@launch
                }

                is ResultWrapper.Success -> result.value
            }
        }
        // 首次询问用户下载的资源(使用第一个资源)
        val (defaultVideoStream, defaultAudioStream) = (popSelectedVideoPartDialog(
            title = CommonLibs.getString(R.string.text_select_the_resource_to_download),
            dash = dashList.first()
        ) as? ResultWrapper.Success)?.value?.let {
            it.videoStream to it.audioStream
        } ?: return@launch
        // 处理用户选择的每一个片段
        partList.forEachIndexed { index, part ->
            val dash = dashList[index]
            // 验证当前视频dash是否存在与用户首次选择一样的流
            val videoStreamVerify = defaultVideoStream?.let { stream ->
                dash.video.find { it.id == stream.id && it.codecId == stream.codecId } != null
            } ?: true
            // 验证当前音频dash是否存在与用户首次选择一样的流
            val audioStreamVerify = defaultAudioStream?.let { stream ->
                dash.audio.find { it.id == stream.id && it.codecId == stream.codecId } != null
            } ?: true
            // 如果当前视频dash存在用户首次选择的视频和音频流，直接下载
            if (videoStreamVerify && audioStreamVerify) {
                startDownload(part, defaultVideoStream, defaultAudioStream)
                return@forEachIndexed
            }
            // 如果不存在则再次询问用户选择
            (popSelectedVideoPartDialog(part.name, dash) as? ResultWrapper.Success)?.let {
                startDownload(part, it.value.videoStream, it.value.audioStream)
            }
        }
        // 执行完所有操作后，退出多选模式
        onSwitchMultipleSelectMode()
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
    ) {
        val resources = mutableListOf<BiliDashModel>().apply {
            videoStream?.let { res ->
                add(BiliDashModel.create(DashType.VIDEO, res))
            }
            audioStream?.let { res ->
                add(BiliDashModel.create(DashType.AUDIO, res))
            }
        }
        DownloadManager.startDownload(CommonLibs.requireContext(), part.bvid, part.cid, resources)
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
        val mid = mBiliUpDataLiveData.value?.mid ?: return
        startActivity(
            PersonalDetailsActivity::class.java,
            PersonalDetailsActivity.buildIntent(mid)
        )
    }
}