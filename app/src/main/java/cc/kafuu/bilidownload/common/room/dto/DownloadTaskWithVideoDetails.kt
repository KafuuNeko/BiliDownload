package cc.kafuu.bilidownload.common.room.dto

import androidx.room.Embedded
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.BiliCodeUtils

data class DownloadTaskWithVideoDetails(
    // DownloadTaskEntity
    @Embedded
    val downloadTask: DownloadTaskEntity,
    // BiliVideoMainEntity
    val title: String,
    val description: String,
    val cover: String,
    // BiliVideoPartEntity
    val partTitle: String
) {
    fun getQualityDetailsVideo(defaultText: String) = downloadTask.dashVideoId?.let {
        BiliCodeUtils.getVideoQualityDescription(it)
    } ?: defaultText

    fun getQualityDetailsAudio(defaultText: String) = downloadTask.dashAudioId?.let {
        BiliCodeUtils.getAudioQualityDescribe(it)
    } ?: defaultText
}