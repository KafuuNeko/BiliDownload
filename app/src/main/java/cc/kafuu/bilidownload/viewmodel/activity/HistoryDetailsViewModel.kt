package cc.kafuu.bilidownload.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.model.DownloadTaskStatus
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.service.DownloadService
import com.arialyy.aria.core.Aria
import kotlinx.coroutines.runBlocking

class HistoryDetailsViewModel : CoreViewModel() {
    // 下载任务信息，包含下载任务实体以及此任务对应的视频和片段的标题、描述、bvid、cid
    val downloadDetailsLiveData = MutableLiveData<DownloadTaskWithVideoDetails?>()

    // 资源列表数据源
    val downloadResourceEntityListLiveData = MutableLiveData<List<DownloadResourceEntity>>()

    // 任务下载进度（百分比）
    val downloadPercentLiveData = MutableLiveData(0)

    // 任务下载进度（具体下载进度）
    val downloadProgressLiveData = MutableLiveData("")

    // 任务暂停
    val downloadIsStoppedLiveData = MutableLiveData(false)

    // 此页面加载状态，loading状态将显示加载动画（默认开启）
    val loadingStatusLiveData = MutableLiveData(LoadingStatus.loadingStatus())

    fun updateVideoDetails(details: DownloadTaskWithVideoDetails?) {
        if (details == null || details.downloadTask.downloadTaskId == null) {
            finishActivity()
            return
        }

        downloadDetailsLiveData.value = details
        updateDownloadStatus()

        loadingStatusLiveData.value = LoadingStatus.doneStatus()
    }

    /**
     * 更新下载状态
     */
    fun updateDownloadStatus() {
        val downloadId = downloadDetailsLiveData.value?.downloadTask?.downloadTaskId ?: return
        Aria.download(this).getGroupEntity(downloadId)?.let {
            val process =
                "${FileUtils.formatFileSize(it.currentProgress)}/${FileUtils.formatFileSize(it.fileSize)}"
            downloadPercentLiveData.postValue(it.percent)
            downloadProgressLiveData.postValue(process)
            val status = DownloadTaskStatus.fromCode(it.state)
            if (status == DownloadTaskStatus.CANCELLED) {
                finishActivity()
            }
            downloadIsStoppedLiveData.postValue(status == DownloadTaskStatus.STOPPED)
        }
    }

    /**
     * 更新资源列表信息
     */
    fun updateDownloadResources(list: List<DownloadResourceEntity>) {
        downloadResourceEntityListLiveData.postValue(list)
    }

    /**
     * 取消当前的下载任务
     */
    fun cancelDownloadTask() {
        val downloadId = downloadDetailsLiveData.value?.downloadTask?.downloadTaskId ?: return
        Aria.download(this).loadGroup(downloadId)?.ignoreCheckPermissions()?.cancel(true)
    }

    /**
     * 重新尝试下载任务
     */
    fun retryDownloadTask() {
        val entityId = downloadDetailsLiveData.value?.downloadTask?.id ?: return
        DownloadService.startDownload(CommonLibs.requireContext(), entityId)
        finishActivity()
    }

    /**
     * 删除任务
     */
    fun deleteDownloadTask() {
        val entity = downloadDetailsLiveData.value?.downloadTask ?: return
        runBlocking { DownloadRepository.deleteDownloadTask(entity.id) }
        entity.downloadTaskId?.let {
            Aria.download(this).loadGroup(it).ignoreCheckPermissions().cancel(true)
        }
        finishActivity()
    }

    /**
     * 暂停或者继续下载任务
     */
    fun pauseOrContinue() {
        val taskId = downloadDetailsLiveData.value?.downloadTask?.downloadTaskId ?: return
        val taskGroup = Aria.download(this).loadGroup(taskId).ignoreCheckPermissions()
        if (downloadIsStoppedLiveData.value == true) taskGroup.resume() else taskGroup.stop()
    }
}