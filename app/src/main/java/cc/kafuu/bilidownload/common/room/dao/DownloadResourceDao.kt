package cc.kafuu.bilidownload.common.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity

@Dao
interface DownloadResourceDao {

    // 插入一个新的ResourceEntity
    @Insert
    suspend fun insert(resourceEntity: DownloadResourceEntity): Long

    // 更新已存在的ResourceEntity
    @Update
    suspend fun update(resourceEntity: DownloadResourceEntity)

    @Query("DELETE FROM DownloadResource WHERE taskEntityId = :taskEntityId")
    suspend fun deleteTaskByTaskEntityId(taskEntityId: Long)

    // 根据ID删除ResourceEntity
    @Query("DELETE FROM DownloadResource WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 查询所有ResourceEntity
    @Query("SELECT * FROM DownloadResource")
    suspend fun getAllResources(): List<DownloadResourceEntity>

    // 根据taskId查询ResourceEntity
    @Query("SELECT * FROM DownloadResource WHERE taskEntityId = :taskId")
    suspend fun getResourcesByTaskId(taskId: Long): List<DownloadResourceEntity>

    // 根据ID查询单个ResourceEntity
    @Query("SELECT * FROM DownloadResource WHERE id = :id")
    suspend fun getResourceById(id: Long): DownloadResourceEntity?
}