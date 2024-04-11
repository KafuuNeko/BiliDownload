package cc.kafuu.bilidownload.common.room.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity

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

    @Query("SELECT * FROM DownloadTaskEntity WHERE status IN (:statuses) ORDER BY id DESC")
    fun getLatestDownloadTaskLiveData(vararg statuses: Int): LiveData<List<DownloadTaskEntity>>

    @Query(
        """
        SELECT task.*, video.title, video.description, video.cover, part.partTitle
        FROM DownloadTaskEntity task
        INNER JOIN BiliVideoMainEntity video ON task.biliBvid = video.biliBvid
        INNER JOIN BiliVideoPartEntity part ON task.biliBvid = part.biliBvid AND task.biliCid = part.biliCid
        WHERE task.status IN (:statuses)
        ORDER BY task.id DESC
    """
    )
    fun getDownloadTasksWithVideoDetailsLiveData(vararg statuses: Int): LiveData<List<DownloadTaskWithVideoDetails>>

    @Query("SELECT * FROM DownloadTaskEntity WHERE id = :id")
    suspend fun getDownloadTaskById(id: Long): DownloadTaskEntity?

    @Query("SELECT * FROM DownloadTaskEntity WHERE downloadTaskId = :downloadTaskId")
    suspend fun getDownloadTaskByDownloadTaskId(downloadTaskId: Long): DownloadTaskEntity?

    @Query("DELETE FROM DownloadTaskEntity")
    suspend fun deleteAll()
}