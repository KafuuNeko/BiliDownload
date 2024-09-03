package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.network.model.BiliSearchManuscriptData
import cc.kafuu.bilidownload.common.network.model.BiliSearchMediaResultData
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliSearchRepository(private val biliApiService: BiliApiService) : BiliRepository() {
    companion object {
        //视频
        private const val SEARCH_TYPE_VIDEO = "video"

        //番剧
        private const val SEARCH_TYPE_MEDIA_BANGUMI = "media_bangumi"

        //影视
        private const val SEARCH_TYPE_MEDIA_FT = "media_ft"
    }

    /**
     * 普通的搜索api链接
     */
    private fun getNormalSearchFullUrl(param: String) =
        NetworkConfig.buildFullUrl("/x/web-interface/wbi/search/type", param)

    /**
     * 用户空间搜索api链接
     */
    private fun getSpaceSearchFullUrl(param: String) =
        NetworkConfig.buildFullUrl("/x/space/wbi/arc/search", param)

    /**
     * 普通的搜索wbi
     */
    private fun <T> requestNormalSearchWbi(
        searchType: String,
        keyword: String,
        page: Int,
        callback: IServerCallback<BiliSearchData<T>>,
        success: (httpCode: Int, code: Int, message: String, data: String) -> Unit
    ) = WbiManager.asyncGenerateSignature(
        linkedMapOf<String, Any>(
            "search_type" to searchType,
            "keyword" to keyword,
            "page" to page
        ),
        object : IServerCallback<String> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                success(httpCode, code, message, data)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        }
    )

    /**
     * 空间搜索wbi
     */
    private fun requestSpaceSearchWbi(
        mid: Long,
        order: String? = null,
        tid: Long? = null,
        keyword: String? = null,
        pn: Int = 1,
        ps: Int = 30,
        callback: IServerCallback<BiliSearchManuscriptData>,
        success: (httpCode: Int, code: Int, message: String, data: String) -> Unit
    ) = WbiManager.asyncGenerateSignature(
        linkedMapOf<String, Any>().apply {
            put("mid", mid)
            order?.let { put("order", it) }
            tid?.let { put("tid", it) }
            keyword?.let { put("keyword", it) }
            put("pn", pn)
            put("ps", ps)
        },
        object : IServerCallback<String> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                success(httpCode, code, message, data)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        }
    )

    /**
     * 全站搜索视频
     */
    fun requestSearchVideo(
        keyword: String,
        page: Int,
        callback: IServerCallback<BiliSearchData<BiliSearchVideoResultData>>
    ) = requestNormalSearchWbi(SEARCH_TYPE_VIDEO, keyword, page, callback) { _, _, _, data ->
        biliApiService.requestSearchVideo(getNormalSearchFullUrl(data)).enqueue(callback) { it }
    }

    /**
     * 全站搜索番剧
     */
    fun requestSearchMediaBangumi(
        keyword: String,
        page: Int,
        callback: IServerCallback<BiliSearchData<BiliSearchMediaResultData>>
    ) = requestNormalSearchWbi(
        SEARCH_TYPE_MEDIA_BANGUMI,
        keyword,
        page,
        callback
    ) { _, _, _, data ->
        biliApiService.requestSearchMedia(getNormalSearchFullUrl(data)).enqueue(callback) { it }
    }

    /**
     * 全站搜索剧集
     */
    fun requestSearchMediaFt(
        keyword: String,
        page: Int,
        callback: IServerCallback<BiliSearchData<BiliSearchMediaResultData>>
    ) = requestNormalSearchWbi(SEARCH_TYPE_MEDIA_FT, keyword, page, callback) { _, _, _, data ->
        biliApiService.requestSearchMedia(getNormalSearchFullUrl(data)).enqueue(callback) { it }
    }

    /**
     * 搜索用户投稿视频
     * @param mid 目标用户mid
     * @param order 排序方式	默认为pubdate, 最新发布：pubdate, 最多播放：click, 最多收藏：stow
     * @param tid 筛选目标分区 默认为0, 0：不进行分区筛选, 分区tid为所筛选的分区
     * @param keyword 关键词筛选 用于使用关键词搜索该UP主视频稿件
     * @param pn 页码 默认为 1
     * @param ps 每页项数 默认为 30
     */
    fun requestSearchManuscript(
        mid: Long,
        order: String? = null,
        tid: Long? = null,
        keyword: String? = null,
        pn: Int = 1,
        ps: Int = 30,
        callback: IServerCallback<BiliSearchManuscriptData>,
    ) = requestSpaceSearchWbi(mid, order, tid, keyword, pn, ps, callback) { _, _, _, data ->
        biliApiService.requestSearchManuscript(getSpaceSearchFullUrl(data)).run {
            enqueue(callback) { it }
        }
    }
}