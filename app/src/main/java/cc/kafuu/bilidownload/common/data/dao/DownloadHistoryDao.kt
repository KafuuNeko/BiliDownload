package cc.kafuu.bilidownload.common.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.data.entity.DownloadHistoryEntity

@Dao
interface DownloadHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(downloadHistory: DownloadHistoryEntity): Long

    @Update
    suspend fun update(downloadHistory: DownloadHistoryEntity)

    @Delete
    suspend fun delete(downloadHistory: DownloadHistoryEntity)

    @Query("SELECT * FROM DownloadHistoryEntity")
    fun getAllDownloadHistories(): LiveData<List<DownloadHistoryEntity>>

    @Query("SELECT * FROM DownloadHistoryEntity WHERE id = :id")
    fun getDownloadHistoryById(id: Long): LiveData<DownloadHistoryEntity>

    @Query("SELECT * FROM DownloadHistoryEntity WHERE isDownloadComplete = :isComplete")
    fun getDownloadsByCompletionStatus(isComplete: Boolean): LiveData<List<DownloadHistoryEntity>>

    @Query("DELETE FROM DownloadHistoryEntity")
    suspend fun deleteAll()
}