package cc.kafuu.bilidownload.model.bili

import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.model.ResourceType

data class BiliStreamResourceModel(
    val resource: BiliPlayStreamResource,
    @ResourceType val type: Int,
)