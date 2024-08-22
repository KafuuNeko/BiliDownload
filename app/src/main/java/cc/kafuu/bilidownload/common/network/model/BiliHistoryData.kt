package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliHistoryData(
    val cursor: BiliHistoryCursor
)

data class BiliHistoryCursor(
    val max: Long,
    @SerializedName("view_at") val viewAt: Long,
    val business: String,
    val ps: Int
)