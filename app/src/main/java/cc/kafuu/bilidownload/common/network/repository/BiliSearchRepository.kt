package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliSearchRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    fun search(keyword: String, callback: IServerCallback<BiliSearchData>) {
        val params = linkedMapOf<String, Any>(
            "keyword" to keyword
        )

        WbiManager.asyncGenerateSignature(params, object : IServerCallback<String> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                biliApiService.search(
                    NetworkConfig.buildFullUrl("/x/web-interface/wbi/search/all/v2", data)
                ).enqueue(callback) { it }
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        })
    }
}