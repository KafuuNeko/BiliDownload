package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import java.io.IOException

class BiliAccountRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    @Throws(IOException::class, IllegalStateException::class)
    fun syncGetAccountData(mid: Long, onFailure: ((Int, Int, String) -> Unit)?): BiliAccountData? {
        val params = linkedMapOf<String, Any>(
            "mid" to mid
        )
        return biliApiService.getAccountData(
            NetworkConfig.buildFullUrl(
                "/x/space/wbi/acc/info", WbiManager.syncGenerateSignature(params)
            )
        ).execute(onFailure) { it }
    }

    fun getAccountData(mid: Long, callback: IServerCallback<BiliAccountData>) {
        val params = linkedMapOf<String, Any>(
            "mid" to mid
        )
        WbiManager.asyncGenerateSignature(params, object : IServerCallback<String> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                biliApiService.getAccountData(
                    NetworkConfig.buildFullUrl(
                        "/x/space/wbi/acc/info", data
                    )
                ).enqueue(callback) { it }
            }
            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        })
    }
}