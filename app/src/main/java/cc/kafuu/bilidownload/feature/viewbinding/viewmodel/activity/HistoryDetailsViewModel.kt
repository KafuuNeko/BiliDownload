package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.viewbinding.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.DownloadStatus
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.LocalResourceActivity
import cc.kafuu.bilidownload.service.DownloadService
import kotlinx.coroutines.runBlocking

class HistoryDetailsViewModel : CoreViewModel() {
    companion object {
        class SaveCoverAction(
            val coverUrl: String,
            val fileName: String
        ) : ViewAction()
        
        class ShowSaveCoverConfirmAction(
            val coverUrl: String,
            val bvid: String
        ) : ViewAction()
    }
    // 下载任务信息，包含下载任务实体以及此任务对应的视频和片段的标题、描述、bvid、cid
    private val mDownloadDetailsLiveData = MutableLiveData<DownloadTaskWithVideoDetails?>()
    val downloadDetailsLiveData = mDownloadDetailsLiveData.liveData()

    // 资源列表数据源
    private val mDownloadResourceEntityListLiveData =
        MutableLiveData<List<DownloadResourceEntity>>()
    val downloadResourceEntityListLiveData = mDownloadResourceEntityListLiveData.liveData()

    // 任务下载进度（百分比）
    private val mDownloadPercentLiveData = MutableLiveData(0)
    val downloadPercentLiveData = mDownloadPercentLiveData.liveData()

    // 任务下载进度（具体下载进度）
    private val mDownloadProgressLiveData = MutableLiveData("")
    val downloadProgressLiveData = mDownloadProgressLiveData.liveData()

    // 任务暂停
    private val mDownloadIsStoppedLiveData = MutableLiveData(false)
    val downloadIsStoppedLiveData = mDownloadIsStoppedLiveData.liveData()

    // 此页面加载状态，loading状态将显示加载动画（默认开启）
    private val mLoadingStatusLiveData = MutableLiveData(LoadingStatus.loadingStatus())
    val loadingStatusLiveData = mLoadingStatusLiveData.liveData()

    fun updateVideoDetails(details: DownloadTaskWithVideoDetails?) {
        if (details == null || details.downloadTask.groupId == null) {
            finishActivity()
            return
        }

        mDownloadDetailsLiveData.value = details
        updateDownloadStatus()

        mLoadingStatusLiveData.value = LoadingStatus.doneStatus()
    }

    /**
     * 更新下载状态
     */
    fun updateDownloadStatus() {
        val downloadId = mDownloadDetailsLiveData.value?.downloadTask?.groupId ?: return
        DownloadManager.getSnapshot(downloadId)?.let {
            val process = if (it.fileSize > 0) {
                "${FileUtils.formatFileSize(it.currentProgress)}/${FileUtils.formatFileSize(it.fileSize)}"
            } else {
                FileUtils.formatFileSize(it.currentProgress)
            }
            mDownloadPercentLiveData.postValue(it.percent)
            mDownloadProgressLiveData.postValue(process)
            val status = it.status
            if (status == DownloadStatus.CANCELLED) {
                finishActivity()
            }
            mDownloadIsStoppedLiveData.postValue(status == DownloadStatus.STOPPED)
        }
    }

    /**
     * 更新资源列表信息
     */
    fun updateDownloadResources(list: List<DownloadResourceEntity>) {
        mDownloadResourceEntityListLiveData.postValue(list)
    }

    /**
     * 取消当前的下载任务
     */
    fun cancelDownloadTask() {
        val downloadId = mDownloadDetailsLiveData.value?.downloadTask?.groupId ?: return
        DownloadManager.cancelDownload(downloadId)
    }

    /**
     * 重新尝试下载任务
     */
    fun retryDownloadTask() {
        val entityId = mDownloadDetailsLiveData.value?.downloadTask?.id ?: return
        DownloadService.startDownload(CommonLibs.requireContext(), entityId)
        finishActivity()
    }

    /**
     * 删除任务
     */
    fun deleteDownloadTask() {
        val entity = mDownloadDetailsLiveData.value?.downloadTask ?: return
        entity.groupId?.let {
            DownloadManager.cancelDownload(it)
        }
        runBlocking { DownloadRepository.deleteDownloadTask(entity.id) }
        finishActivity()
    }

    /**
     * 暂停或者继续下载任务
     */
    fun pauseOrContinue() {
        val task = mDownloadDetailsLiveData.value?.downloadTask ?: return
        val groupId = task.groupId ?: return
        if (downloadIsStoppedLiveData.value == true) {
            DownloadService.startDownload(CommonLibs.requireContext(), task.id)
        } else {
            DownloadManager.stopDownload(groupId)
        }
    }

    fun entryResource(data: DownloadResourceEntity) {
        startActivity(
            LocalResourceActivity::class.java,
            LocalResourceActivity.buildIntent(data)
        )
    }

    /**
     * 封面图片点击事件
     */
    fun onCoverClick() {
        val details = mDownloadDetailsLiveData.value ?: return
        val coverUrl = details.cover
        val bvid = details.downloadTask.biliBvid
        
        sendViewAction(ShowSaveCoverConfirmAction(coverUrl, bvid))
    }
    
    /**
     * 保存封面图片
     */
    private fun onSaveCover(coverUrl: String, bvid: String) {
        // 从URL中提取扩展名，默认为jpg
        val extension = when {
            coverUrl.contains(".jpg", ignoreCase = true) || coverUrl.contains(".jpeg", ignoreCase = true) -> ".jpg"
            coverUrl.contains(".png", ignoreCase = true) -> ".png"
            coverUrl.contains(".webp", ignoreCase = true) -> ".webp"
            coverUrl.contains(".gif", ignoreCase = true) -> ".gif"
            else -> ".jpg"
        }
        
        // 使用 bv 号作为默认文件名
        val fileName = "${bvid}$extension"
        sendViewAction(SaveCoverAction(coverUrl, fileName))
    }
    
    /**
     * 确认保存封面（由 Activity 调用）
     */
    fun confirmSaveCover(coverUrl: String, bvid: String) {
        onSaveCover(coverUrl, bvid)
    }
}
