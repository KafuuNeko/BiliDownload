package cc.kafuu.bilidownload.common.data.dao

import androidx.lifecycle.LiveData
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
    fun getAllDownloadTask(): LiveData<List<DownloadTaskEntity>>

    @Query("SELECT * FROM DownloadTaskEntity WHERE id = :id")
    fun getDownloadTaskById(id: Long): LiveData<DownloadTaskEntity>

    @Query("SELECT * FROM DownloadTaskEntity WHERE isDownloadComplete = :isComplete")
    fun getDownloadsByCompletionStatus(isComplete: Boolean): LiveData<List<DownloadTaskEntity>>

    @Query("DELETE FROM DownloadTaskEntity")
    suspend fun deleteAll()
}