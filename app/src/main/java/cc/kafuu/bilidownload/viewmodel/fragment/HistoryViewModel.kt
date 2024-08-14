package cc.kafuu.bilidownload.viewmodel.fragment

import androidx.lifecycle.LiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.view.activity.HistoryDetailsActivity
import com.arialyy.aria.core.Aria
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class HistoryViewModel : RVViewModel() {
    val centerCrop = CenterCrop()

    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskWithVideoDetails>>
        private set

    fun initData(status: List<TaskStatus>) {
        latestDownloadTaskLiveData = DownloadRepository.queryDownloadTasksDetailsLiveData(status)
    }

    fun getStatusIcon(task: DownloadTaskWithVideoDetails) = CommonLibs.getDrawable(
        when (TaskStatus.entries.find { it.code == task.downloadTask.status }) {
            TaskStatus.PREPARE -> R.drawable.ic_prepare
            TaskStatus.DOWNLOADING -> R.drawable.ic_downloading
            TaskStatus.DOWNLOAD_FAILED -> R.drawable.ic_download_failed_cloud
            TaskStatus.SYNTHESIS -> R.drawable.ic_synthesis
            TaskStatus.SYNTHESIS_FAILED -> R.drawable.ic_synthesis_failed
            TaskStatus.COMPLETED -> R.drawable.ic_download_done_cloud
            else -> R.drawable.ic_unknown_med
        }
    )

    fun getStatusText(task: DownloadTaskWithVideoDetails): String {
        val percent = task.downloadTask.groupId?.let {
            Aria.download(this).loadGroup(it).percent
        }
        return "${percent ?: 0}%"
    }

    fun entryHistoryDetails(task: DownloadTaskWithVideoDetails) = startActivity(
        HistoryDetailsActivity::class.java,
        HistoryDetailsActivity.buildIntent(task.downloadTask.id)
    )
}