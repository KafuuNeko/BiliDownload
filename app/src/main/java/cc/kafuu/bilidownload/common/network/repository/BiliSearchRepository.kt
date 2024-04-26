package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliSearchRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    companion object {
        //视频
        const val SEARCH_TYPE_VIDEO = "video"
        //番剧
        const val SEARCH_TYPE_MEDIA_BANGUMI = "media_bangumi"
        //影视
        const val SEARCH_TYPE_MEDIA_FT = "media_ft"
    }
    fun search(searchType: String, keyword: String, page: Int, callback: IServerCallback<BiliSearchData>) {
        val params = linkedMapOf<String, Any>(
            "search_type" to searchType,
            "keyword" to keyword,
            "page" to page
        )

        WbiManager.asyncGenerateSignature(params, object : IServerCallback<String> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                biliApiService.search(
                    NetworkConfig.buildFullUrl("/x/web-interface/wbi/search/type", data)
                ).enqueue(callback) { it }
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        })
    }
}