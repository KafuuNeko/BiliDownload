package cc.kafuu.bilidownload.viewmodel.activity

import android.util.Log
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.model.IAsyncCallback
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.LocalMediaDetail
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.FFMpegUtils

class LocalResourceVideModel : CoreViewModel() {
    companion object {
        private const val TAG = "LocalResourceVideModel"
    }

    // 此页面加载状态，loading状态将显示加载动画（默认开启）
    val loadingStatusLiveData = MutableLiveData(LoadingStatus.loadingStatus())

    // 此资源隶属的任务详情
    val taskDetailLiveData = MutableLiveData<DownloadTaskWithVideoDetails>()

    // 下载的资源实体
    val resourceLiveData = MutableLiveData<DownloadResourceEntity>()

    // 此资源文件信息
    val localMediaDetailLiveData = MutableLiveData<LocalMediaDetail>()

    fun updateResourceEntity(resource: DownloadResourceEntity) {
        resourceLiveData.value = resource
        doLoadResourceDetails(resource)
    }

    fun updateTaskDetails(details: DownloadTaskWithVideoDetails) {
        taskDetailLiveData.value = details
    }

    private fun doLoadResourceDetails(resource: DownloadResourceEntity) {
        object : IAsyncCallback<LocalMediaDetail, Exception> {
            override fun onSuccess(data: LocalMediaDetail) {
                Log.d(TAG, "onSuccess: $data")
                localMediaDetailLiveData.postValue(data)
            }

            override fun onFailure(exception: Exception) {
                exception.printStackTrace()
                loadingStatusLiveData.postValue(
                    LoadingStatus.errorStatus(message = exception.message ?: "Unknown exception")
                )
            }
        }.also { FFMpegUtils.getMediaInfo(resource.file, it) }
    }

    /**
     * 校验数据是否已经全部加载完成，若已全部加载完成则设置加载状态为完成状态
     */
    fun checkLoaded() {
        if (
            taskDetailLiveData.value != null &&
            resourceLiveData.value != null &&
            localMediaDetailLiveData.value != null
        ) {
            loadingStatusLiveData.postValue(LoadingStatus.doneStatus())
        }
    }
}