package cc.kafuu.bilidownload.common.model.bili

import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.constant.DashType
import java.io.Serializable

data class BiliStreamResourceModel(
    val resource: BiliPlayStreamResource,
    @DashType val type: Int,
): Serializable