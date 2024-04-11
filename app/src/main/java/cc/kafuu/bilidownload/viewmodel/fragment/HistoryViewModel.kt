package cc.kafuu.bilidownload.viewmodel.fragment

import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs
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
                DownloadTaskEntity.STATUS_DOWNLOADING -> R.drawable.ic_download
                else -> R.drawable.ic_download
            }
        )
    }

}