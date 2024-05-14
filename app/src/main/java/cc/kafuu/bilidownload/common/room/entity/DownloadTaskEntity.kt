package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.utils.MimeTypeUtils
import java.io.File

@Entity(tableName = "DownloadTask")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    var downloadTaskId: Long? = null,
    var status: Int = STATUS_PREPARE,
    val biliBvid: String,
    val biliCid: Long,
    val createTime: Long = System.currentTimeMillis()
) {
    companion object {
        // 准备好下载
        const val STATUS_PREPARE = 0

        // 正在下载
        const val STATUS_DOWNLOADING = 1

        // 下载失败
        const val STATUS_DOWNLOAD_FAILED = -1

        // 正在进行音视频合成处理
        const val STATUS_SYNTHESIS = 2

        // 合成处理失败
        const val STATUS_SYNTHESIS_FAILED = -2

        // 下载任务完成（下载与视频合并）
        const val STATUS_COMPLETED = 3

        fun createEntity(
            bvid: String,
            cid: Long,
        ) = DownloadTaskEntity(
            biliBvid = bvid,
            biliCid = cid,
        )
    }

}