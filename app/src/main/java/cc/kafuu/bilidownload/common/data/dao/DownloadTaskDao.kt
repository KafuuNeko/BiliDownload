package cc.kafuu.bilidownload.common.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(downloadTask: DownloadTaskEntity): Long

    @Update
    suspend fun update(downloadTask: DownloadTaskEntity)

    @Delete
    suspend fun delete(downloadTask: DownloadTaskEntity)

    @Query("SELECT * FROM DownloadTaskEntity")
    suspend fun getAllDownloadTask(): List<DownloadTaskEntity>

    @Query("SELECT * FROM DownloadTaskEntity WHERE id = :id")
    suspend fun getDownloadTaskById(id: Long): DownloadTaskEntity?

    @Query("SELECT * FROM DownloadTaskEntity WHERE downloadTaskId = :downloadTaskId")
    suspend fun getDownloadTaskByDownloadTaskId(downloadTaskId: Long): DownloadTaskEntity?

    @Query("SELECT * FROM DownloadTaskEntity WHERE isDownloadComplete = :isComplete")
    suspend fun getDownloadsByCompletionStatus(isComplete: Boolean): List<DownloadTaskEntity>

    @Query("DELETE FROM DownloadTaskEntity")
    suspend fun deleteAll()
}