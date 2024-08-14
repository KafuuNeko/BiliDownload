package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import cc.kafuu.bilidownload.common.model.TaskStatus

@Entity(tableName = "DownloadTask")
data class DownloadTaskEntity(
    // 下载任务ID
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 下载组ID（每个下载任务都拥有一个对应的下载组）
    var groupId: Long? = null,
    // 当前下载任务状态
    var status: Int = TaskStatus.PREPARE.code,
    // BVID
    val biliBvid: String,
    // CID
    val biliCid: Long,
    // 下载任务创建时间
    val createTime: Long = System.currentTimeMillis()
) {
    companion object {
        fun createEntity(
            bvid: String,
            cid: Long,
        ) = DownloadTaskEntity(
            biliBvid = bvid,
            biliCid = cid,
        )
    }

}