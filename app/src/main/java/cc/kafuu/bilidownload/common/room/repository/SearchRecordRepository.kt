package cc.kafuu.bilidownload.common.room.repository

import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.SearchType
import cc.kafuu.bilidownload.common.room.entity.SearchRecordEntity

object SearchRecordRepository {
    private const val MAX_COUNT = 100
    private val mSearchRecordDao by lazy { CommonLibs.requireAppDatabase().searchRecordDao() }

    fun observe() = mSearchRecordDao.observe(10)

    suspend fun add(keyword: String, @SearchType searchType: Int) {
        val record = mSearchRecordDao.queryByKeyword(keyword)
            ?.copy(time = System.currentTimeMillis())
            ?: SearchRecordEntity(keyword = keyword, searchType = searchType)
        mSearchRecordDao.insertOrReplace(record)
        if (mSearchRecordDao.count() > MAX_COUNT) mSearchRecordDao.deleteOldest()
    }
}