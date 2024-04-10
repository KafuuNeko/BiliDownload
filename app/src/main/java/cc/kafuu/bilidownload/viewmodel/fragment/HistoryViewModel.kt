package cc.kafuu.bilidownload.viewmodel.fragment

import android.util.Log
import androidx.lifecycle.LiveData
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs

class HistoryViewModel : RVViewModel() {
    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskEntity>>

    fun initData(vararg statuses: Int) {
        latestDownloadTaskLiveData = CommonLibs.requireAppDatabase().downloadTaskDao().getLatestDownloadTaskLiveData(*statuses)
    }

}