package cc.kafuu.bilidownload.common.model.bili

import cc.kafuu.bilidownload.common.model.DashType
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource

data class BiliDashModel (
    @DashType val type: Int,
    val dashId: Long,
    val codecId: Long,
    val mimeType: String,
    val codecs: String,
) {
    companion object {
        fun create(@DashType dashType: Int, resource: BiliPlayStreamResource) = BiliDashModel (
            type = dashType,
            dashId = resource.id,
            codecId = resource.codecId,
            mimeType = resource.mimeType,
            codecs = resource.codecs
        )
    }
}