package cc.kafuu.bilidownload.viewmodel.fragment

import android.util.Log
import androidx.lifecycle.LiveData
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs

class HistoryViewModel : RVViewModel() {
    private val mDownloadTaskDao = CommonLibs.requireAppDatabase().downloadTaskDao()
    private lateinit var mStatuses: IntArray

    lateinit var latestDownloadTaskLiveData: LiveData<List<DownloadTaskEntity>>
    var latestDownloadTaskId: Long = 0
    var oldestDownloadTaskId: Long = 0

    suspend fun initData(vararg statuses: Int) {
        mStatuses = statuses
        fetchHistory()
        latestDownloadTaskLiveData = mDownloadTaskDao.getLatestDownloadTaskLiveData(1, *statuses)
    }

    suspend fun fetchHistory(limit: Long = 10, lastId: Long? = null): Int {
        val newData = if (lastId == null) {
            mDownloadTaskDao.getLatestDownloadTasks(limit, *mStatuses).apply {
                latestDownloadTaskId = maxOfOrNull { it.id } ?: 0
            }
        } else {
            mDownloadTaskDao.getDownloadTasksPagedAfter(limit, lastId, *mStatuses)
        }
        oldestDownloadTaskId = (newData.minOfOrNull { it.id } ?: Long.MAX_VALUE).let {
            if (it >= oldestDownloadTaskId) oldestDownloadTaskId else it
        }
        val originalData = listMutableLiveData.value
        listMutableLiveData.value = if (originalData == null || lastId == null) {
            newData.toMutableList()
        } else {
            originalData.apply { addAll(newData) }
        }
        return newData.size
    }

    fun removeHistoryEntity(entity: DownloadTaskEntity) {
        listMutableLiveData.value?.let { list ->
            list.remove(entity)
            listMutableLiveData.value = list
        }
    }

    fun onNewDataReceived(list: List<DownloadTaskEntity>) {
        val currentList = listMutableLiveData.value ?: return
        currentList.addAll(0, list)
        latestDownloadTaskId = list.maxOfOrNull { it.id } ?: latestDownloadTaskId
        Log.d("TAG", "onNewDataReceived: $latestDownloadTaskId")
        listMutableLiveData.value = currentList
    }

}