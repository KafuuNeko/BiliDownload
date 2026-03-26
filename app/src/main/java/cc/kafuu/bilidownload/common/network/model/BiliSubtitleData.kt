package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliSubtitleListContainer(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: BiliSubtitleListData?
) {
    fun isSuccess() = code == 0
}

data class BiliSubtitleListData(
    @SerializedName("subtitle") val subtitle: BiliSubtitleInfo?
)

data class BiliSubtitleInfo(
    @SerializedName("subtitles") val subtitles: List<BiliSubtitleUrlItem>?
)

data class BiliSubtitleUrlItem(
    @SerializedName("id") val id: Long,
    @SerializedName("lan") val lan: String,
    @SerializedName("lan_doc") val lanDoc: String,
    @SerializedName("subtitle_url") val subtitleUrl: String,
    @SerializedName("type") val type: Int
)

data class BccSubtitle(
    @SerializedName("body") val body: List<BccItem>?
)

data class BccItem(
    @SerializedName("from") val from: Double,
    @SerializedName("to") val to: Double,
    @SerializedName("content") val content: String
)
