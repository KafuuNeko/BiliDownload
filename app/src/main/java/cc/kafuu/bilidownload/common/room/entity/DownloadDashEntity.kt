package cc.kafuu.bilidownload.common.room.entity

import androidx.room.Entity
import cc.kafuu.bilidownload.common.utils.BiliCodeUtils
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.utils.MimeTypeUtils
import cc.kafuu.bilidownload.common.model.DashType
import java.io.File

@Entity(primaryKeys = ["dashId", "taskEntityId", "codecId"], tableName = "DownloadDash")
data class DownloadDashEntity (
    val dashId: Long,
    val taskEntityId: Long,
    val codecId: Long,
    @DashType val type: Int,
    val mimeType: String,
    val codecs: String,
) {
    fun getOutputFile(): File {
        // 根据mimetype取得文件后缀名
        val suffix = MimeTypeUtils.getExtensionFromMimeType(mimeType) ?: "bin"
        // 取得合成文件输出路径
        return File(CommonLibs.requireResourcesDir(), "stream-$taskEntityId-$dashId-$codecId.$suffix")
    }

    fun getQualityDetails(defaultText: String) = when(type) {
        DashType.AUDIO -> BiliCodeUtils.getAudioQualityDescribe(dashId)
        DashType.VIDEO -> BiliCodeUtils.getVideoQualityDescription(dashId)
        else -> defaultText
    }
}