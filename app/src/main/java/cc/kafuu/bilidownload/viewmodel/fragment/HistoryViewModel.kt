package cc.kafuu.bilidownload.viewmodel.fragment

import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs
import com.arialyy.aria.core.Aria
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class HistoryViewModel : RVViewModel() {
    val centerCrop = CenterCrop()

    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskWithVideoDetails>>

    fun initData(vararg statuses: Int) {
        latestDownloadTaskLiveData = CommonLibs.requireAppDatabase().downloadTaskDao()
            .getDownloadTasksWithVideoDetailsLiveData(*statuses)
    }

    fun getStatusIcon(task: DownloadTaskWithVideoDetails): Drawable? {
        return CommonLibs.getDrawable(
            when (task.downloadTask.status) {
                DownloadTaskEntity.STATUS_PREPARE -> R.drawable.ic_prepare
                DownloadTaskEntity.STATUS_DOWNLOADING -> R.drawable.ic_downloading
                DownloadTaskEntity.STATUS_DOWNLOAD_FAILED -> R.drawable.ic_download_failed_cloud
                DownloadTaskEntity.STATUS_SYNTHESIS -> R.drawable.ic_synthesis
                DownloadTaskEntity.STATUS_SYNTHESIS_FAILED -> R.drawable.ic_synthesis_failed
                DownloadTaskEntity.STATUS_COMPLETED -> R.drawable.ic_download_done_cloud
                else -> R.drawable.ic_unknown_med
            }
        )
    }

    fun getStatusText(task: DownloadTaskWithVideoDetails): String {
        val percent =
            task.downloadTask.downloadTaskId?.let { Aria.download(this).loadGroup(it).percent }
        return "${percent ?: 0}%"
    }

}