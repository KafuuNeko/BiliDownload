package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliSeasonData(
    val activity: BiliSeasonActivity?,
    val alias: String?,
    @SerializedName("bkg_cover") val bkgCover: String?,
    val cover: String,
    val episodes: List<BiliSeasonEpisode>,
    val evaluate: String,
    @SerializedName("jp_title") val jpTitle: String?,
    val link: String,
    @SerializedName("media_id") val mediaId: Int,
    val mode: Int,
    val record: String?,
    @SerializedName("season_id") val seasonId: Int,
    @SerializedName("season_title") val seasonTitle: String,
    @SerializedName("share_copy") val shareCopy: String,
    @SerializedName("share_sub_title") val shareSubTitle: String,
    @SerializedName("share_url") val shareUrl: String,
    @SerializedName("square_cover") val squareCover: String?,
    val status: Int,
    val subtitle: String,
    val title: String,
    val total: Int,
    val type: Int,
)

data class BiliSeasonActivity(
    @SerializedName("head_bg_url") val headBgUrl: String?,
    val id: Int,
    val title: String
)

data class BiliSeasonEpisode(
    val aid: Int,
    val badge: String?,
    val bvid: String,
    val cid: Long,
    val cover: String,
    val from: String,
    val id: Int,
    val link: String,
    @SerializedName("long_title") val longTitle: String,
    @SerializedName("pub_time") val pubTime: Long,
    val pv: Int,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("share_copy") val shareCopy: String,
    @SerializedName("share_url") val shareUrl: String,
    @SerializedName("short_link") val shortLink: String,
    val status: Int,
    val subtitle: String,
    val title: String,
    val vid: String,
)