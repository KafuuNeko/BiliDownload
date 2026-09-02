package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliWatchLaterData(
    val count: Int,
    val list: List<BiliWatchLaterItem>?
)

data class BiliWatchLaterItem(
    val aid: Long,
    val bvid: String?,
    val title: String?,
    @SerializedName("pic") val cover: String?,
    @SerializedName("desc") val description: String?,
    @SerializedName("pubdate") val pubDate: Long,
    val duration: Long,
    val owner: BiliWatchLaterOwner?,
    val cid: Long?,
    @SerializedName("add_at") val addAt: Long
)

data class BiliWatchLaterOwner(
    val name: String?
)
