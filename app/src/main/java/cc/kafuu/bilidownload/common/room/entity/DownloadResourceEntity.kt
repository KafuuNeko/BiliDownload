package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import cc.kafuu.bilidownload.common.constant.DownloadResourceType

@Entity(tableName = "DownloadResource")
data class DownloadResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    @DownloadResourceType val type: Int,
    val name: String,
    val mimeType: String,
    val storageSizeBytes: Long,
    val creationTime: Long,
    val file: String
)
