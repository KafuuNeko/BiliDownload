package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliHistoryData(
    // 历史记录页面信息
    val cursor: BiliHistoryCursor,
    // 历史记录筛选类型
    val tab: List<BiliHistoryTab>,
    // 分段历史记录列表
    val list: List<BiliHistoryItem>
)

data class BiliHistoryCursor(
    // 最后一项目标 id
    val max: Long,
    // 最后一项时间节点
    @SerializedName("view_at") val viewAt: Long,
    // 最后一项业务类型
    val business: String,
    // 每页项数
    val ps: Int
)

data class BiliHistoryTab(
    // 类型
    val type: String,
    // 类型名
    val name: String,
)

data class BiliHistoryItem(
    // 条目标题
    val title: String,

    // 条目副标题
    @SerializedName("long_title") val longTitle: String,

    // 条目封面图 url，仅用于专栏以外的条目
    val cover: String?,

    // 条目封面图组，仅用于专栏，封面有效时为数组，无效时为 null
    val covers: List<String>?,

    // 重定向 url，仅用于剧集和直播
    val uri: String?,

    // 条目详细信息对象
    val history: BiliHistoryDetails,

    // 视频分 P 数目，仅用于稿件视频
    val videos: Int?,

    // UP 主昵称
    @SerializedName("author_name") val authorName: String,

    // UP 主头像 url
    @SerializedName("author_face") val authorFace: String,

    // UP 主 mid
    @SerializedName("author_mid") val authorMid: Long,

    // 查看时间，时间戳
    @SerializedName("view_at") val viewAt: Long,

    // 视频观看进度，单位为秒，用于稿件视频或剧集
    val progress: Int?,

    // 角标文案，稿件视频 / 剧集 / 笔记
    val badge: String?,

    // 分 P 标题，用于稿件视频或剧集
    @SerializedName("show_title") val showTitle: String?,

    // 视频总时长，用于稿件视频或剧集
    val duration: Long?,

    // 总计分集数，仅用于剧集
    val total: Int?,

    // 最新一话 / 最新一 P 标识，用于稿件视频或剧集
    @SerializedName("new_desc") val newDesc: String?,

    // 是否已完结，仅用于剧集。0：未完结，1：已完结
    @SerializedName("is_finish") val isFinish: Int,

    // 是否收藏，0：未收藏，1：已收藏
    @SerializedName("is_fav") val isFav: Int,

    // 条目目标 id
    val kid: Long,

    // 子分区名，用于稿件视频和直播
    @SerializedName("tag_name") val tagName: String?,

    // 直播状态，仅用于直播。0：未开播，1：已开播
    @SerializedName("live_status") val liveStatus: Int?
)

data class BiliHistoryDetails(
    // 目标 id，稿件视频&剧集的 avid 或直播间 id，或文章 cvid，文集 rlid
    val oid: Long,

    // 剧集 epid，仅用于剧集
    val epid: Long?,

    // 稿件 bvid，仅用于稿件视频
    val bvid: String?,

    // 观看到的视频分 P 数，仅用于稿件视频
    val page: Int?,

    // 观看到的对象 id，稿件视频&剧集的 cid 或文章 cvid
    val cid: Long?,

    // 观看到的视频分 P 标题，仅用于稿件视频
    val part: String?,

    // 业务类型
    // archive：稿件
    // pgc：剧集（番剧 / 影视）
    // live：直播
    // article-list：文集
    // article：文章
    val business: String,

    // 记录查看的平台代码，1 3 5 7：手机端，2：web端，4 6：pad端，33：TV端，0：其他
    val dt: Int
)