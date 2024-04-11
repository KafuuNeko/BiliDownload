package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskEntityId: Long,
    val name: String,
    val mimeType: String,
    val storageSizeBytes: Long,
    val creationTime: Long,
    val file: String
)
