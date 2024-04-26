package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.network.model.BiliSearchMediaResultData
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliSearchRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    companion object {
        //视频
        private const val SEARCH_TYPE_VIDEO = "video"

        //番剧
        private const val SEARCH_TYPE_MEDIA_BANGUMI = "media_bangumi"

        //影视
        private const val SEARCH_TYPE_MEDIA_FT = "media_ft"
    }

    private fun makeParams(searchType: String, keyword: String, page: Int) =
        linkedMapOf<String, Any>(
            "search_type" to searchType,
            "keyword" to keyword,
            "page" to page
        )

    private fun getFullUrl(param: String) =
        NetworkConfig.buildFullUrl("/x/web-interface/wbi/search/type", param)

    private fun <T> searchCheckWbi(
        searchType: String,
        keyword: String,
        page: Int,
        callback: IServerCallback<BiliSearchData<T>>,
        success: (httpCode: Int, code: Int, message: String, data: String) -> Unit
    ) {
        val params = makeParams(searchType, keyword, page)
        WbiManager.asyncGenerateSignature(params, object : IServerCallback<String> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                success(httpCode, code, message, data)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        })
    }

    fun searchVideo(
        keyword: String,
        page: Int,
        callback: IServerCallback<BiliSearchData<BiliSearchVideoResultData>>
    ) {
        searchCheckWbi(SEARCH_TYPE_VIDEO, keyword, page, callback) { _, _, _, data ->
            biliApiService.searchVideo(getFullUrl(data)).enqueue(callback) { it }
        }
    }

    fun searchMediaBangumi(
        keyword: String,
        page: Int,
        callback: IServerCallback<BiliSearchData<BiliSearchMediaResultData>>
    ) {
        searchCheckWbi(SEARCH_TYPE_MEDIA_BANGUMI, keyword, page, callback) { _, _, _, data ->
            biliApiService.searchMedia(getFullUrl(data)).enqueue(callback) { it }
        }
    }

    fun searchMediaFt(
        keyword: String,
        page: Int,
        callback: IServerCallback<BiliSearchData<BiliSearchMediaResultData>>
    ) {
        searchCheckWbi(SEARCH_TYPE_MEDIA_FT, keyword, page, callback) { _, _, _, data ->
            biliApiService.searchMedia(getFullUrl(data)).enqueue(callback) { it }
        }
    }
}