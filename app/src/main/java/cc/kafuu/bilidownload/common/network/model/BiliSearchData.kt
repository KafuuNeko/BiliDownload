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
    @SerializedName("exp_list") val expList: Any?,
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
    @SerializedName("badgepay") val badgePay: Boolean,
    @SerializedName("hit_columns") val hitColumns: List<String>,
    @SerializedName("view_type") val viewType: String?,
    @SerializedName("is_pay") val isPay: Int,
    @SerializedName("is_union_video") val isUnionVideo: Int,
    @SerializedName("rec_tags") val recTags: Any?,
    @SerializedName("new_rec_tags") val newRecTags: List<Any?>,
    @SerializedName("rank_score") val rankScore: Int
)

data class BiliSearchMediaResultData(
    val type: String,
    val mediaId: Long,
    val seasonId: Long,
    val title: String,
    val orgTitle: String?,
    val cover: String,
    val mediaType: Int,
    val areas: String,
    val styles: String,
    val cv: String,
    val staff: String,
    val playState: Int,
    @SerializedName("goto_url") val gotoUrl: String,
    val desc: String,
    val corner: Int,
    @SerializedName("pubtime") val pubTime: Long,
)