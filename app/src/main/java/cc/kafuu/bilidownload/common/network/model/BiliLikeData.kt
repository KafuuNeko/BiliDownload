package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliLikeListData(
    @SerializedName("list") val list: List<BiliLikeVideoData>?
)

data class BiliLikeVideoData(
    @SerializedName("bvid") val bvid: String,
    @SerializedName("title") val title: String,
    @SerializedName("pic") val pic: String,
    @SerializedName("desc") val description: String,
    @SerializedName("pubdate") val pubDate: Long,
    @SerializedName("duration") val duration: Long,
    @SerializedName("owner") val owner: BiliLikeVideoOwner
)

data class BiliLikeVideoOwner(
    @SerializedName("name") val name: String
)
