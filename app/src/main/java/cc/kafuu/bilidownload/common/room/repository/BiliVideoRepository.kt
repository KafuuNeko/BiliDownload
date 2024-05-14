package cc.kafuu.bilidownload.common.room.repository

import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.room.entity.BiliVideoMainEntity
import cc.kafuu.bilidownload.common.room.entity.BiliVideoPartEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs

object BiliVideoRepository {
    private val mBiliVideoDao by lazy { CommonLibs.requireAppDatabase().biliVideoDao() }

    suspend fun doInsertOrUpdateVideoDetails(biliVideoData: BiliVideoData, cid: Long) {
        // 插入或更新bv视频信息
        val biliVideoMainEntity = BiliVideoMainEntity(
            biliVideoData.bvid,
            biliVideoData.owner.name,
            biliVideoData.owner.mid,
            biliVideoData.title,
            biliVideoData.desc,
            biliVideoData.pic
        )
        mBiliVideoDao.insertOrUpdate(biliVideoMainEntity)
        // 插入或更新片段信息
        biliVideoData.pages.find { it.cid == cid }?.let { page ->
            BiliVideoPartEntity(
                biliVideoData.bvid,
                page.cid,
                page.part
            ).let { mBiliVideoDao.insertOrUpdate(it) }
        }
    }
}