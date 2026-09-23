package cc.kafuu.bilidownload.common.network.model

import cc.kafuu.bilidownload.common.model.bili.VideoStats
import com.google.gson.annotations.SerializedName

data class BiliVideoData(
    @SerializedName("bvid") val bvid: String,
    @SerializedName("aid") val aid: Long,
    @SerializedName("videos") val videos: Int,
    @SerializedName("tid") val tid: Long,
    @SerializedName("tname") val tName: String,
    @SerializedName("copyright") val copyright: Int,
    @SerializedName("pic") val pic: String,
    @SerializedName("title") val title: String,
    @SerializedName("pubdate") val pubDate: Long,
    @SerializedName("ctime") val cTime: Long,
    @SerializedName("desc") val desc: String,
    @SerializedName("state") val state: Int,
    @SerializedName("duration") val duration: Long,
    @SerializedName("rights") val rights: BiliVideoRights,
    @SerializedName("owner") val owner: BiliVideoOwner,
    @SerializedName("stat") val stat: BiliVideoStat? = null,
    @SerializedName("dynamic") val dynamic: String,
    @SerializedName("cid") val cid: Long,
    @SerializedName("dimension") val dimension: BiliVideoDimension,
    @SerializedName("pages") val pages: List<BiliVideoPage>,
    @SerializedName("subtitle") val subtitle: BiliVideoSubtitle,
    @SerializedName("staff") val staff: List<BiliVideoStaff>
)

data class BiliVideoRights(
    @SerializedName("bp") val bp: Int,
    @SerializedName("elec") val elec: Int,
    @SerializedName("download") val download: Int,
    @SerializedName("movie") val movie: Int,
    @SerializedName("pay") val pay: Int,
    @SerializedName("hd5") val hd5: Int,
    @SerializedName("no_reprint") val noReprint: Int,
    @SerializedName("autoplay") val autoplay: Int,
    @SerializedName("ugc_pay") val ugcPay: Int,
    @SerializedName("is_cooperation") val isCooperation: Int,
    @SerializedName("ugc_pay_preview") val ugcPayPreview: Int,
    @SerializedName("no_background") val noBackground: Int,
    @SerializedName("clean_mode") val cleanMode: Int,
    @SerializedName("is_stein_gate") val isSteinGate: Int,
    @SerializedName("is_360") val is360: Int,
    @SerializedName("no_share") val noShare: Int,
    @SerializedName("arc_pay") val arcPay: Int,
    @SerializedName("free_watch") val freeWatch: Int
)

data class BiliVideoOwner(
    @SerializedName("mid") val mid: Long,
    @SerializedName("name") val name: String,
    @SerializedName("face") val face: String
)

/** 详情及列表内嵌统计；可选字段缺失时保留未知状态。 */
data class BiliVideoStat(
    @SerializedName("view") val view: Long? = null,
    @SerializedName("danmaku") val danmaku: Long? = null,
    @SerializedName("reply") val reply: Long? = null,
    @SerializedName("favorite") val favorite: Long? = null,
    @SerializedName("coin") val coin: Int,
    @SerializedName("share") val share: Int,
    @SerializedName("now_rank") val nowRank: Int,
    @SerializedName("his_rank") val hisRank: Int,
    @SerializedName("like") val like: Long? = null,
    @SerializedName("dislike") val dislike: Int,
    @SerializedName("evaluation") val evaluation: String?,
) {
    /** 转换为可展示的统计，负数占位值不作为真实计数。 */
    fun toVideoStats() = VideoStats(view, like, favorite, danmaku, reply).normalized()
}

data class BiliVideoDimension(
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("rotate") val rotate: Int
)

data class BiliVideoPage(
    @SerializedName("cid") val cid: Long,
    @SerializedName("page") val page: Int,
    @SerializedName("from") val from: String,
    @SerializedName("part") val part: String,
    @SerializedName("duration") val duration: Long,
    @SerializedName("vid") val vid: String?,
    @SerializedName("weblink") val weblink: String?,
    @SerializedName("dimension") val dimension: BiliVideoDimension
)

data class BiliVideoSubtitle(
    @SerializedName("allow_submit") val allowSubmit: Boolean,
    @SerializedName("list") val list: List<BiliVideoSubtitleItem>
)

data class BiliVideoSubtitleItem(
    @SerializedName("id") val id: Long,
    @SerializedName("lan") val lan: String,
    @SerializedName("lan_doc") val lanDoc: String,
    @SerializedName("is_lock") val isLock: Boolean,
    @SerializedName("author_mid") val authorMid: Long,
    @SerializedName("subtitle_url") val subtitleUrl: String,
    @SerializedName("author") val author: BiliVideoSubtitleAuthor
)

data class BiliVideoSubtitleAuthor(
    @SerializedName("mid") val mid: Long,
    @SerializedName("name") val name: String,
    @SerializedName("sex") val sex: String,
    @SerializedName("face") val face: String,
    @SerializedName("sign") val sign: String,
)

data class BiliVideoStaff(
    @SerializedName("mid") val mid: Long,
    @SerializedName("title") val title: String,
    @SerializedName("name") val name: String,
    @SerializedName("face") val face: String,
    @SerializedName("vip") val vip: BiliVideoVip,
    @SerializedName("official") val official: BiliVideoOfficial,
    @SerializedName("follower") val follower: Int
)

data class BiliVideoVip(
    @SerializedName("type") val type: Int,
    @SerializedName("status") val status: Int,
    @SerializedName("theme_type") val themeType: Int
)

data class BiliVideoOfficial(
    @SerializedName("role") val role: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("desc") val desc: String?,
    @SerializedName("type") val type: Int
)
