package cc.kafuu.bilidownload.common.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity
data class DownloadTaskEntity (
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long? = null,
    val isDownloadComplete: Boolean,
    val requestUrl: String,
    val biliBvid: String,
    val biliCid: Long,
    val biliQn: Int,
    val dashVideoId: Long,
    val dashAudioId: Long
): Serializable