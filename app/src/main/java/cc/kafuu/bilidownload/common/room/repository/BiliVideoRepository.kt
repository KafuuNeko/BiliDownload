package cc.kafuu.bilidownload.common.room.repository

import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.room.entity.BiliVideoMainEntity
import cc.kafuu.bilidownload.common.room.entity.BiliVideoPartEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs

object BiliVideoRepository {
    private val mBiliVideoDao = CommonLibs.requireAppDatabase().biliVideoDao()

    suspend fun doInsertOrUpdateVideoDetails(biliVideoData: BiliVideoData) {
        // 插入或更新bv视频信息
        val biliVideoMainEntity = BiliVideoMainEntity(
            biliVideoData.bvid,
            biliVideoData.owner.name,
            biliVideoData.owner.mid,
            biliVideoData.title,
            biliVideoData.desc,
            biliVideoData.pic
        )
        mBiliVideoDao.insert(biliVideoMainEntity)
        // 插入或更新次bv的所有子视频信息
        val biliVideoPartEntityList = biliVideoData.pages.map {
            BiliVideoPartEntity(
                biliVideoData.bvid,
                it.cid,
                it.part
            )
        }
        mBiliVideoDao.insert(*biliVideoPartEntityList.toTypedArray())
    }
}