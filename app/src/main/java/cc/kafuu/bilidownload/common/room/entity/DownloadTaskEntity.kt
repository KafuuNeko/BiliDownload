package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DownloadTask")
data class DownloadTaskEntity(
    // 下载任务ID
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 下载组ID（每个下载任务都拥有一个对应的下载组）
    var groupId: Long? = null,
    // 当前下载任务状态
    var status: Int = STATE_PREPARE,
    // BVID
    val biliBvid: String,
    // CID
    val biliCid: Long,
    // 下载任务创建时间
    val createTime: Long = System.currentTimeMillis()
) {
    companion object {
        // 准备好下载
        const val STATE_PREPARE = 0

        // 正在下载
        const val STATE_DOWNLOADING = 1

        // 下载失败
        const val STATE_DOWNLOAD_FAILED = -1

        // 正在进行音视频合成处理
        const val STATE_SYNTHESIS = 2

        // 合成处理失败
        const val STATE_SYNTHESIS_FAILED = -2

        // 下载任务完成（下载与视频合并）
        const val STATE_COMPLETED = 3

        fun createEntity(
            bvid: String,
            cid: Long,
        ) = DownloadTaskEntity(
            biliBvid = bvid,
            biliCid = cid,
        )
    }

}