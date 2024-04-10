package cc.kafuu.bilidownload.common.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.data.entity.BiliVideoPartEntity

@Dao
interface BiliVideoPartDao {
    // 插入一个或多个BiliVideoPartEntity，如果存在冲突，则替换
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg videoParts: BiliVideoPartEntity)

    // 更新已存在的BiliVideoPartEntity
    @Update
    suspend fun update(videoPart: BiliVideoPartEntity)

    // 删除一个BiliVideoPartEntity
    @Delete
    suspend fun delete(videoPart: BiliVideoPartEntity)

    // 通过bvid查询一个BiliVideoPartEntity
    @Query("SELECT * FROM BiliVideoPartEntity WHERE bvid = :bvid")
    suspend fun getVideoPartByBvid(bvid: String): BiliVideoPartEntity

    // 查询所有BiliVideoPartEntity
    @Query("SELECT * FROM BiliVideoPartEntity")
    suspend fun getAllVideoParts(): List<BiliVideoPartEntity>

    // 通过partTitle模糊查询BiliVideoPartEntity
    @Query("SELECT * FROM BiliVideoPartEntity WHERE partTitle LIKE :partTitle")
    suspend fun searchVideoPartsByTitle(partTitle: String): List<BiliVideoPartEntity>
}