package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliFavoriteListData(
    val count: Int,
    val list: List<BiliFavoriteData>?
)

data class BiliFavoriteDetailsData(
    @SerializedName("info")
    val favoriteData: BiliFavoriteData,
    @SerializedName("medias")
    val medias: List<BiliFavoriteMedia>?,
)

data class BiliFavoriteData(
    // 收藏夹mlid（完整id), 收藏夹原始id+创建者mid尾号2位
    val id: Long,
    // 收藏夹原始id
    val fid: Long,
    // 创建者mid
    val mid: Long,
    // 收藏夹标题
    val title: String,
    // 目标id是否存在于该收藏夹	存在于该收藏夹：1, 不存在于该收藏夹：0
    @SerializedName("fav_state")
    val favState: Int,
    // 收藏夹内容数量
    @SerializedName("media_count")
    val mediaCount: Int,
    // 收藏夹封面图片url
    val cover: String?,
)

data class BiliFavoriteMedia(
    //视频稿件：视频稿件avid; 音频：音频auido; 视频合集：视频合集id
    val id: Long,
    //2：视频稿件 12：音频 21：视频合集
    val type: Int,
    // 标题
    val title: String,
    // 封面url
    val cover: String,
    // 简介
    val intro: String,
    // 视频分P数
    val page: Int,
    // 音频/视频时长
    val duration: Long,
    // UP主信息
    val upper: BiliFavoriteUpper,
    // 投稿时间
    @SerializedName("ctime")
    val cTime: Long,
    @SerializedName("pubtime")
    // 发布时间
    val pubTime: Long,
    // 收藏时间
    @SerializedName("fav_time")
    val favTime: Long,
    // 视频稿件bvid
    val bvid: String?
)

data class BiliFavoriteUpper(
    val mid: Long,
    val name: String,
    val face: String
)