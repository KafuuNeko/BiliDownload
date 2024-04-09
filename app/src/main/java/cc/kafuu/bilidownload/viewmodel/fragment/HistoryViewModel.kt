package cc.kafuu.bilidownload.viewmodel.fragment

import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs

class HistoryViewModel : RVViewModel() {
    private val mDownloadTaskDao = CommonLibs.requireAppDatabase().downloadTaskDao()

    suspend fun fetchHistory(statuses: IntArray, limit: Long = 10, lastId: Long? = null): Int {
        val newData = if (lastId == null) {
            mDownloadTaskDao.getLatestDownloadTasks(limit, *statuses)
        } else {
            mDownloadTaskDao.getDownloadTasksPagedAfter(limit, lastId, *statuses)
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


}