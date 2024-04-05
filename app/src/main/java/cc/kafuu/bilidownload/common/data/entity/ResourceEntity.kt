package cc.kafuu.bilidownload.common.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val downloadHistoryId: Long,
    val fileName: String,
    val mimeType: String,
    val storageSizeBytes: Long,
    val creationTime: Long
)
