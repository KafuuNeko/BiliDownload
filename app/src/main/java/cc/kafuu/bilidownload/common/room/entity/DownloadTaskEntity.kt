package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DownloadTask")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    var downloadTaskId: Long? = null,
    var status: Int = STATE_PREPARE,
    val biliBvid: String,
    val biliCid: Long,
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