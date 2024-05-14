package cc.kafuu.bilidownload.common.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity

@Dao
interface DownloadDashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(vararg dashList: DownloadDashEntity)

    @Query("SELECT * FROM DownloadDash WHERE taskEntityId = :id")
    suspend fun queryDashListByTaskEntityId(id: Long): List<DownloadDashEntity>

    @Query("DELETE FROM DownloadDash WHERE taskEntityId = :taskEntityId")
    suspend fun deleteTaskByTaskEntityId(taskEntityId: Long)
}