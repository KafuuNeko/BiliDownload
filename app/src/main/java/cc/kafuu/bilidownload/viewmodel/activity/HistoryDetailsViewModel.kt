package cc.kafuu.bilidownload.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import com.arialyy.aria.core.Aria

class HistoryDetailsViewModel : CoreViewModel() {
    val downloadDetailsLiveData = MutableLiveData<DownloadTaskWithVideoDetails?>()
    val downloadStatusLiveData = MutableLiveData<DownloadManager.TaskStatus>()
    val loadingStatusLiveData = MutableLiveData(LoadingStatus.loadingStatus())

    fun updateVideoDetails(details: DownloadTaskWithVideoDetails?) {
        if (details == null || details.downloadTask.downloadTaskId == null) {
            finishActivity()
            return
        }

        downloadDetailsLiveData.value = details
        loadingStatusLiveData.value = LoadingStatus.doneStatus()

        Aria.download(this).loadGroup(details.downloadTask.downloadTaskId!!)?.let {
            downloadStatusLiveData.value = DownloadManager.TaskStatus.fromCode(it.entity.state)
        }
    }
}