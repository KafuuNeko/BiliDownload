package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

data class BiliSearchData<R>(
    val seid: String,
    val page: Int,
    @SerializedName("page_size") val pageSize: Int,
    @SerializedName("numResults") val numResults: Int,
    @SerializedName("numPages") val numPages: Int,
    @SerializedName("suggest_keyword") val suggestKeyword: String?,
    @SerializedName("rqt_type") val rqtType: String,
    @SerializedName("cost_time") val costTime: BiliSearchCostTime,
    @SerializedName("egg_hit") val eggHit: Int,
    @SerializedName("pageinfo") val pageInfo: BiliSearchPageInfo,
    @SerializedName("top_tlist") val topTList: BiliSearchTopTList,
    @SerializedName("show_column") val showColumn: Int,
    @SerializedName("show_module_list") val showModuleList: List<String>,
    @SerializedName("result") val result: List<R>?
)

data class BiliSearchCostTime(
    @SerializedName("params_check") val paramsCheck: String,
    @SerializedName("illegal_handler") val illegalHandler: String,
    @SerializedName("as_response_format") val asResponseFormat: String,
    @SerializedName("as_request") val asRequest: String,
    @SerializedName("save_cache") val saveCache: String,
    @SerializedName("deserialize_response") val deserializeResponse: String,
    @SerializedName("as_request_format") val asRequestFormat: String,
    @SerializedName("total") val total: String,
    @SerializedName("main_handler") val mainHandler: String
)

data class BiliSearchPageInfo(
    val pgc: BiliSearchCategoryInfo,
    @SerializedName("live_room") val liveRoom: BiliSearchCategoryInfo,
    val photo: BiliSearchCategoryInfo,
    val topic: BiliSearchCategoryInfo,
    val video: BiliSearchCategoryInfo,
    val user: BiliSearchCategoryInfo,
    @SerializedName("bili_user") val biliUser: BiliSearchCategoryInfo,
    @SerializedName("media_ft") val mediaFt: BiliSearchCategoryInfo,
    val article: BiliSearchCategoryInfo,
    @SerializedName("media_bangumi") val mediaBangumi: BiliSearchCategoryInfo,
    val special: BiliSearchCategoryInfo,
    @SerializedName("operation_card") val operationCard: BiliSearchCategoryInfo,
    val upuser: BiliSearchCategoryInfo,
    val movie: BiliSearchCategoryInfo,
    @SerializedName("live_all") val liveAll: BiliSearchCategoryInfo,
    val tv: BiliSearchCategoryInfo,
    val live: BiliSearchCategoryInfo,
    val bangumi: BiliSearchCategoryInfo,
    val activity: BiliSearchCategoryInfo,
    @SerializedName("live_master") val liveMaster: BiliSearchCategoryInfo,
    @SerializedName("live_user") val liveUser: BiliSearchCategoryInfo
)

data class BiliSearchCategoryInfo(
    @SerializedName("numResults") val numResults: Int,
    val total: Int,
    val pages: Int
)

data class BiliSearchTopTList(
    val pgc: Int,
    @SerializedName("live_room") val liveRoom: Int,
    val photo: Int,
    val topic: Int,
    val video: Int,
    val user: Int,
    @SerializedName("bili_user") val biliUser: Int,
    @SerializedName("media_ft") val mediaFt: Int,
    val article: Int,
    @SerializedName("media_bangumi") val mediaBangumi: Int,
    val card: Int,
    @SerializedName("operation_card") val operationCard: Int,
    val upuser: Int,
    val movie: Int,
    @SerializedName("live_all") val liveAll: Int,
    val tv: Int,
    val live: Int,
    val special: Int,
    val bangumi: Int,
    val activity: Int,
    @SerializedName("live_master") val liveMaster: Int,
    @SerializedName("live_user") val liveUser: Int
)

data class BiliSearchVideoResultData(
    val type: String,
    val id: Long,
    val author: String,
    val mid: Long,
    @SerializedName("typeid") val typeId: String,
    @SerializedName("typename") val typeName: String,
    @SerializedName("arcurl") val arcUrl: String,
    val aid: Long,
    val bvid: String,
    val title: String,
    val description: String,
    @SerializedName("arcrank") val arcRank: String,
    val pic: String,
    val play: Int,
    @SerializedName("video_review") val videoReview: Int,
    val favorites: Int,
    val tag: String,
    val review: Int,
    @SerializedName("pubdate") val pubDate: Long,
    @SerializedName("senddate") val sendDate: Long,
    val duration: String,
)

data class BiliSearchMediaResultData(
    val type: String,
    @SerializedName("media_id") val mediaId: Long,
    @SerializedName("season_id") val seasonId: Long,
    val title: String,
    @SerializedName("org_title") val orgTitle: String?,
    val cover: String,
    @SerializedName("media_type") val mediaType: Int,
    val styles: String,
    val cv: String,
    val staff: String,
    @SerializedName("goto_url") val gotoUrl: String,
    val desc: String,
    @SerializedName("pubtime") val pubTime: Long,
)

data class BiliSearchManuscriptData(
    // 列表信息
    val list: BiliSearchManuscriptList,
    // 页面信息
    val page: BiliSearchManuscriptPage,
    // “播放全部”按钮信息
    @SerializedName("episodic_button") val episodicButton: BiliSearchManuscriptEpisodicButton
)

data class BiliSearchManuscriptList(
    // 投稿视频分区索引，以 {tid} 为键的对象
    @SerializedName("tlist") val typeList: Map<String, BiliSearchManuscriptType>,
    // 投稿视频列表
    @SerializedName("vlist") val videoList: List<BiliSearchManuscriptVideo>
)

// 数据类：BiliSearchManuscriptTlist，表示 tlist 对象中的 {tid} 分区详情
data class BiliSearchManuscriptType(
    // 投稿至该分区的视频数
    val count: Int,
    // 该分区名称
    val name: String,
    // 该分区的 ID
    val tid: Long
)

data class BiliSearchManuscriptVideo(
    // 稿件 avid
    val aid: Long,
    // 视频属性
    val attribute: Int,
    // 视频 UP 主，不一定为目标用户（合作视频时可能为他人）
    val author: String,
    // 稿件 bvid
    val bvid: String,
    // 视频评论数
    val comment: Int,
    // 视频版权类型
    val copyright: String,
    // 投稿时间，时间戳格式
    val created: Long,
    // 视频简介
    val description: String,
    // 是否付费，默认为 0
    @SerializedName("is_pay") val isPay: Int,
    // 是否为合作视频，0 表示否，1 表示是
    @SerializedName("is_union_video") val isUnionVideo: Int,
    // 视频长度，格式为 MM:SS
    val length: String,
    // 视频 UP 主的 mid，不一定为目标用户
    val mid: Long,
    // 视频封面 URL
    val pic: String,
    // 视频播放次数
    val play: Int,
    // 审核相关字段，默认为 0
    val review: Int,
    // 视频的字幕信息，默认为空
    val subtitle: String,
    // 视频标题
    val title: String,
    // 视频分区 ID
    @SerializedName("typeid") val typeId: Long,
    // 视频弹幕数
    @SerializedName("video_review") val videoReview: Int
)

data class BiliSearchManuscriptPage(
    // 总计稿件数
    val count: Int,
    // 当前页码
    val pn: Int,
    // 每页的项数
    val ps: Int
)

data class BiliSearchManuscriptEpisodicButton(
    // 按钮的文字
    val text: String,
    // 全部播放页的 URL
    val uri: String
)
