package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.model.MyBiliAccountData
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import cc.kafuu.bilidownload.common.network.service.BiliPassportService
import com.google.gson.JsonObject
import java.io.IOException

class BiliAccountRepository(
    private val biliApiService: BiliApiService,
    private val biliPassportService: BiliPassportService
) : BiliRepository() {
    @Throws(IOException::class, IllegalStateException::class)
    fun syncRequestAccountData(
        mid: Long,
        onFailure: ((Int, Int, String) -> Unit)?
    ): BiliAccountData? {
        val params = linkedMapOf<String, Any>(
            "mid" to mid
        )
        return biliApiService.requestAccountData(
            NetworkConfig.buildFullUrl(
                "/x/space/wbi/acc/info", WbiManager.syncGenerateSignature(params)
            )
        ).execute(onFailure) { it }
    }

    fun requestAccountData(mid: Long, callback: IServerCallback<BiliAccountData>) {
        val params = linkedMapOf<String, Any>(
            "mid" to mid
        )
        WbiManager.asyncGenerateSignature(params, object : IServerCallback<String> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                biliApiService.requestAccountData(
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

    fun requestMyAccountData(
        callback: IServerCallback<MyBiliAccountData>
    ) = biliApiService.requestMyAccount().enqueue(callback) {
        it
    }

    fun requestLogout(
        biliCSRF: String,
        callback: IServerCallback<JsonObject>
    ) = biliPassportService.requestLogout(
        biliCSRF
    ).enqueue(callback) { it }
}