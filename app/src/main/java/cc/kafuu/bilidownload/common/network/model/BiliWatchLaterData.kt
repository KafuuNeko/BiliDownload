package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliWatchLaterData(
    val count: Int,
    val list: List<BiliWatchLaterItem>?
)

/** 稍后再看稿件，兼容服务端未返回统计的条目。 */
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
    @SerializedName("add_at") val addAt: Long,
    val stat: BiliVideoStat? = null,
)

data class BiliWatchLaterOwner(
    val name: String?
)
