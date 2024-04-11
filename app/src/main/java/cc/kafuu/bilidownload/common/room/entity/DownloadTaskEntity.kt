package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.utils.MimeTypeUtils
import java.io.File

@Entity
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    var downloadTaskId: Long? = null,
    var status: Int = STATUS_PREPARE,
    var progress: Int = 0,
    val biliBvid: String,
    val biliCid: Long,
    val dashVideoId: Long,
    val dashVideoMimeType: String,
    val dashVideoCodecs: String,
    val dashVideoCodecId: Long,
    val dashAudioId: Long,
    val dashAudioMimeType: String,
    val dashAudioCodecs: String,
    val dashAudioCodecId: Long,
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
            videoId: Long,
            videoMimeType: String,
            videoCodecs: String,
            videoCodecId: Long,
            audioId: Long,
            audioMimeType: String,
            audioCodecs: String,
            audioCodecId: Long
        ) = DownloadTaskEntity(
            biliBvid = bvid,
            biliCid = cid,
            dashVideoId = videoId,
            dashVideoMimeType = videoMimeType,
            dashVideoCodecs = videoCodecs,
            dashVideoCodecId = videoCodecId,
            dashAudioId = audioId,
            dashAudioMimeType = audioMimeType,
            dashAudioCodecs = audioCodecs,
            dashAudioCodecId = audioCodecId
        )

        fun createEntity(
            bvid: String,
            cid: Long,
            video: BiliPlayStreamResource,
            audio: BiliPlayStreamResource
        ) = DownloadTaskEntity(
            biliBvid = bvid,
            biliCid = cid,
            dashVideoId = video.id,
            dashVideoMimeType = video.mimeType,
            dashVideoCodecs = video.codecs,
            dashVideoCodecId = video.codecId,
            dashAudioId = audio.id,
            dashAudioMimeType = audio.mimeType,
            dashAudioCodecs = audio.codecs,
            dashAudioCodecId = audio.codecId
        )
    }


    fun getDefaultOutputFile(): File? {
        // 根据mimetype取得文件后缀名
        val suffix = MimeTypeUtils.getExtensionFromMimeType(dashVideoMimeType)
            ?: return null
        // 取得合成文件输出路径
        return File(CommonLibs.requireResourcesDir(), "${id}.$suffix")
    }

}