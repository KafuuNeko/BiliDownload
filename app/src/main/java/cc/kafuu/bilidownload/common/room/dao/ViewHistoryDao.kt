package cc.kafuu.bilidownload.common.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.kafuu.bilidownload.common.room.entity.ViewHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ViewHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: ViewHistoryEntity)

    @Query("DELETE FROM ViewHistory WHERE bvid = :bvid")
    suspend fun deleteByBvid(bvid: String)

    @Query("SELECT * FROM ViewHistory ORDER BY viewTime DESC")
    fun getAllHistory(): Flow<List<ViewHistoryEntity>>

    @Query("SELECT * FROM ViewHistory ORDER BY viewTime DESC LIMIT :limit")
    suspend fun getHistoryLimit(limit: Int): List<ViewHistoryEntity>

    @Query("SELECT * FROM ViewHistory WHERE bvid = :bvid")
    suspend fun getHistoryByBvid(bvid: String): ViewHistoryEntity?

    @Query("DELETE FROM ViewHistory")
    suspend fun deleteAll()

    @Query("DELETE FROM ViewHistory WHERE bvid IN (SELECT bvid FROM ViewHistory ORDER BY viewTime DESC LIMIT -1 OFFSET 100)")
    suspend fun deleteExcessRecords()
}
