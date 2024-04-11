package cc.kafuu.bilidownload.common.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cc.kafuu.bilidownload.common.room.entity.BiliVideoMainEntity

@Dao
interface BiliVideoMainDao {

    // 插入一个或多个BiliVideoMainEntity，如果存在冲突，则替换
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg video: BiliVideoMainEntity)

    // 更新已存在的BiliVideoMainEntity
    @Update
    suspend fun update(video: BiliVideoMainEntity)

    // 删除一个BiliVideoMainEntity
    @Delete
    suspend fun delete(video: BiliVideoMainEntity)

    // 通过bvid删除单个视频信息
    @Query("DELETE FROM BiliVideoMainEntity WHERE bvid = :bvid")
    suspend fun deleteVideoByBvid(bvid: String)

    // 通过bvid查询单个视频信息
    @Query("SELECT * FROM BiliVideoMainEntity WHERE bvid = :bvid")
    suspend fun getVideoByBvid(bvid: String): BiliVideoMainEntity?

    // 查询所有视频信息
    @Query("SELECT * FROM BiliVideoMainEntity")
    suspend fun getAllVideos(): List<BiliVideoMainEntity>
}