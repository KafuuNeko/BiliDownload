package cc.kafuu.bilidownload.common.model.bili

import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.model.DashType

data class BiliStreamResourceModel(
    val resource: BiliPlayStreamResource,
    @DashType val type: Int,
)