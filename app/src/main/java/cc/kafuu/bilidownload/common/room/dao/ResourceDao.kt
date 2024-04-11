package cc.kafuu.bilidownload.common.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.room.entity.ResourceEntity

@Dao
interface ResourceDao {

    // 插入一个新的ResourceEntity
    @Insert
    suspend fun insert(resourceEntity: ResourceEntity): Long

    // 更新已存在的ResourceEntity
    @Update
    suspend fun update(resourceEntity: ResourceEntity)

    // 根据ID删除ResourceEntity
    @Query("DELETE FROM ResourceEntity WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 查询所有ResourceEntity
    @Query("SELECT * FROM ResourceEntity")
    suspend fun getAllResources(): List<ResourceEntity>

    // 根据taskId查询ResourceEntity
    @Query("SELECT * FROM ResourceEntity WHERE taskEntityId = :taskId")
    suspend fun getResourcesByTaskId(taskId: Long): List<ResourceEntity>

    // 根据ID查询单个ResourceEntity
    @Query("SELECT * FROM ResourceEntity WHERE id = :id")
    suspend fun getResourceById(id: Long): ResourceEntity?
}