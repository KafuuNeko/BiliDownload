package cc.kafuu.bilidownload.common.room.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.kafuu.bilidownload.common.room.entity.SearchRecordEntity

@Dao
interface SearchRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(downloadTask: SearchRecordEntity): Long

    @Delete
    suspend fun delete(downloadTask: SearchRecordEntity)

    @Query("SELECT * FROM SearchRecord ORDER BY time DESC LIMIT :limit")
    fun observe(limit: Int): LiveData<List<SearchRecordEntity>>

    @Query("SELECT * FROM SearchRecord WHERE keyword = :keyword LIMIT 1")
    suspend fun queryByKeyword(keyword: String): SearchRecordEntity?

    @Query("SELECT * FROM SearchRecord ORDER BY time DESC LIMIT :limit")
    suspend fun queryLatest(limit: Int): List<SearchRecordEntity>

    @Query("DELETE FROM SearchRecord WHERE id = (SELECT id FROM SearchRecord ORDER BY time ASC LIMIT 1)")
    suspend fun deleteOldest()

    @Query("SELECT COUNT(*) FROM SearchRecord")
    suspend fun count(): Int
}