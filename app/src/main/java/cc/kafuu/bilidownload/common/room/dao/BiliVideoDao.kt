package cc.kafuu.bilidownload.common.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.room.entity.BiliVideoMainEntity
import cc.kafuu.bilidownload.common.room.entity.BiliVideoPartEntity

@Dao
interface BiliVideoDao {
    // 插入一个或多个BiliVideoMainEntity，如果存在冲突，则替换
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(vararg video: BiliVideoMainEntity)

    // 更新已存在的BiliVideoMainEntity
    @Update
    suspend fun update(video: BiliVideoMainEntity)

    // 删除一个BiliVideoMainEntity
    @Delete
    suspend fun delete(video: BiliVideoMainEntity)

    // 通过bvid删除单个视频信息
    @Query("DELETE FROM BiliVideoMain WHERE biliBvid = :bvid")
    suspend fun deleteVideoByBvid(bvid: String)

    // 通过bvid查询单个视频信息
    @Query("SELECT * FROM BiliVideoMain WHERE biliBvid = :bvid")
    suspend fun getVideoByBvid(bvid: String): BiliVideoMainEntity?

    // 查询所有视频信息
    @Query("SELECT * FROM BiliVideoMain")
    suspend fun getAllVideos(): List<BiliVideoMainEntity>

    // 插入一个或多个BiliVideoPartEntity，如果存在冲突，则替换
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(vararg videoParts: BiliVideoPartEntity)

    // 更新已存在的BiliVideoPartEntity
    @Update
    suspend fun update(videoPart: BiliVideoPartEntity)

    // 删除一个BiliVideoPartEntity
    @Delete
    suspend fun delete(videoPart: BiliVideoPartEntity)

    // 通过bvid查询一个BiliVideoPartEntity
    @Query("SELECT * FROM BiliVideoPart WHERE biliBvid = :bvid")
    suspend fun getVideoPartByBvid(bvid: String): BiliVideoPartEntity

    // 查询所有BiliVideoPartEntity
    @Query("SELECT * FROM BiliVideoPart")
    suspend fun getAllVideoParts(): List<BiliVideoPartEntity>

    // 通过partTitle模糊查询BiliVideoPartEntity
    @Query("SELECT * FROM BiliVideoPart WHERE partTitle LIKE :partTitle")
    suspend fun searchVideoPartsByTitle(partTitle: String): List<BiliVideoPartEntity>
}