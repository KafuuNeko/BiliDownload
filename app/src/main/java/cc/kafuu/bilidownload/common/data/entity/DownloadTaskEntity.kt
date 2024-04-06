package cc.kafuu.bilidownload.common.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    var downloadTaskId: Long? = null,
    val isDownloadComplete: Boolean,
    val biliBvid: String,
    val biliCid: Long,
    val biliQn: Int,
    val dashVideoId: Long,
    val dashAudioId: Long
)