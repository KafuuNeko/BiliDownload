package cc.kafuu.bilidownload.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.model.DownloadTaskStatus
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.service.DownloadService
import cc.kafuu.bilidownload.view.activity.LocalResourceActivity
import com.arialyy.aria.core.Aria
import kotlinx.coroutines.runBlocking

class HistoryDetailsViewModel : CoreViewModel() {
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
        Aria.download(this).getGroupEntity(downloadId)?.let {
            val process =
                "${FileUtils.formatFileSize(it.currentProgress)}/${FileUtils.formatFileSize(it.fileSize)}"
            mDownloadPercentLiveData.postValue(it.percent)
            mDownloadProgressLiveData.postValue(process)
            val status = DownloadTaskStatus.fromCode(it.state)
            if (status == DownloadTaskStatus.CANCELLED) {
                finishActivity()
            }
            mDownloadIsStoppedLiveData.postValue(status == DownloadTaskStatus.STOPPED)
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
        Aria.download(this).loadGroup(downloadId)?.ignoreCheckPermissions()?.cancel(true)
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
        runBlocking { DownloadRepository.deleteDownloadTask(entity.id) }
        entity.groupId?.let {
            Aria.download(this).loadGroup(it).ignoreCheckPermissions().cancel(true)
        }
        finishActivity()
    }

    /**
     * 暂停或者继续下载任务
     */
    fun pauseOrContinue() {
        val taskId = mDownloadDetailsLiveData.value?.downloadTask?.groupId ?: return
        val taskGroup = Aria.download(this).loadGroup(taskId).ignoreCheckPermissions()
        if (downloadIsStoppedLiveData.value == true) taskGroup.resume() else taskGroup.stop()
    }

    fun entryResource(data: DownloadResourceEntity) {
        startActivity(
            LocalResourceActivity::class.java,
            LocalResourceActivity.buildIntent(data)
        )
    }
}