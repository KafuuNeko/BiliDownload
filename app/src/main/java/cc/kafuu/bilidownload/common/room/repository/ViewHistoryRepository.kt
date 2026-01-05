package cc.kafuu.bilidownload.common.room.repository

import android.content.Context
import cc.kafuu.bilidownload.common.room.AppDatabase
import cc.kafuu.bilidownload.common.room.entity.ViewHistoryEntity
import kotlinx.coroutines.flow.Flow

class ViewHistoryRepository(context: Context) {
    private val viewHistoryDao = AppDatabase.requireInstance(context).viewHistoryDao()

    suspend fun addViewHistory(
        bvid: String,
        title: String,
        cover: String,
        author: String
    ) {
        val history = ViewHistoryEntity(
            bvid = bvid,
            title = title,
            cover = cover,
            author = author,
            viewTime = System.currentTimeMillis()
        )
        viewHistoryDao.insertOrUpdate(history)
        // 删除超过100条的记录
        viewHistoryDao.deleteExcessRecords()
    }

    suspend fun deleteByBvid(bvid: String) {
        viewHistoryDao.deleteByBvid(bvid)
    }

    suspend fun deleteAll() {
        viewHistoryDao.deleteAll()
    }

    fun getAllHistory(): Flow<List<ViewHistoryEntity>> {
        return viewHistoryDao.getAllHistory()
    }

    suspend fun getHistoryByBvid(bvid: String): ViewHistoryEntity? {
        return viewHistoryDao.getHistoryByBvid(bvid)
    }
}
