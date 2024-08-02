package cc.kafuu.bilidownload.viewmodel.fragment

import androidx.lifecycle.LiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.view.activity.HistoryDetailsActivity
import com.arialyy.aria.core.Aria
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class HistoryViewModel : RVViewModel() {
    val centerCrop = CenterCrop()

    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskWithVideoDetails>>
        private set

    fun initData(vararg statuses: Int) {
        latestDownloadTaskLiveData = DownloadRepository.queryDownloadTasksDetailsLiveData(
            *statuses
        )
    }

    fun getStatusIcon(task: DownloadTaskWithVideoDetails) = CommonLibs.getDrawable(
        when (task.downloadTask.status) {
            DownloadTaskEntity.STATE_PREPARE -> R.drawable.ic_prepare
            DownloadTaskEntity.STATE_DOWNLOADING -> R.drawable.ic_downloading
            DownloadTaskEntity.STATE_DOWNLOAD_FAILED -> R.drawable.ic_download_failed_cloud
            DownloadTaskEntity.STATE_SYNTHESIS -> R.drawable.ic_synthesis
            DownloadTaskEntity.STATE_SYNTHESIS_FAILED -> R.drawable.ic_synthesis_failed
            DownloadTaskEntity.STATE_COMPLETED -> R.drawable.ic_download_done_cloud
            else -> R.drawable.ic_unknown_med
        }
    )

    fun getStatusText(task: DownloadTaskWithVideoDetails): String {
        val percent = task.downloadTask.downloadTaskId?.let {
            Aria.download(this).loadGroup(it).percent
        }
        return "${percent ?: 0}%"
    }

    fun entryHistoryDetails(task: DownloadTaskWithVideoDetails) = startActivity(
        HistoryDetailsActivity::class.java,
        HistoryDetailsActivity.buildIntent(task.downloadTask.id)
    )
}