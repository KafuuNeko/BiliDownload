package cc.kafuu.bilidownload.viewmodel.fragment

import androidx.lifecycle.LiveData
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.utils.CommonLibs

class HistoryViewModel : RVViewModel() {
    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskWithVideoDetails>>

    fun initData(vararg statuses: Int) {
        latestDownloadTaskLiveData = CommonLibs.requireAppDatabase().downloadTaskDao()
            .getDownloadTasksWithVideoDetailsLiveData(*statuses)
    }

}