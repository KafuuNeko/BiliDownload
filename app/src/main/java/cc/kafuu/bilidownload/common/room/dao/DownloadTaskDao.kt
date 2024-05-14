package cc.kafuu.bilidownload.common.room.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity

@Dao
interface DownloadTaskDao {
    @Insert
    suspend fun insert(downloadTask: DownloadTaskEntity): Long

    @Update
    suspend fun update(downloadTask: DownloadTaskEntity)

    @Delete
    suspend fun delete(downloadTask: DownloadTaskEntity)

    @Query("DELETE FROM DownloadTask WHERE id = :taskEntityId")
    suspend fun deleteTaskByTaskEntityId(taskEntityId: Long)

    @Query("SELECT * FROM DownloadTask WHERE status IN (:statuses)")
    suspend fun getLatestDownloadTask(vararg statuses: Int): List<DownloadTaskEntity>

    @Query(
        """
        SELECT task.*, video.title, video.description, video.cover, part.partTitle
        FROM DownloadTask task
        INNER JOIN BiliVideoMain video ON task.biliBvid = video.biliBvid
        INNER JOIN BiliVideoPart part ON task.biliBvid = part.biliBvid AND task.biliCid = part.biliCid
        WHERE task.status IN (:statuses)
        ORDER BY task.id DESC
    """
    )
    fun getDownloadTasksWithVideoDetailsLiveData(vararg statuses: Int): LiveData<List<DownloadTaskWithVideoDetails>>

    @Query("SELECT * FROM DownloadTask WHERE id = :id")
    suspend fun getDownloadTaskById(id: Long): DownloadTaskEntity?

    @Query("SELECT * FROM DownloadTask WHERE downloadTaskId = :downloadTaskId")
    suspend fun getDownloadTaskByDownloadTaskId(downloadTaskId: Long): DownloadTaskEntity?

    @Query("DELETE FROM DownloadTask")
    suspend fun deleteAll()
}