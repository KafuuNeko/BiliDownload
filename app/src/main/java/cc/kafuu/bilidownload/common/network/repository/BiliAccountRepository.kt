package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteDetailsData
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteListData
import cc.kafuu.bilidownload.common.network.model.MyBiliAccountData
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import cc.kafuu.bilidownload.common.network.service.BiliPassportService
import com.google.gson.JsonObject
import java.io.IOException

class BiliAccountRepository(
    private val biliApiService: BiliApiService,
    private val biliPassportService: BiliPassportService
) : BiliRepository() {
    /**
     * 通过mid获取账户数据（异步方式）
     */
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

    /**
     * 通过mid获取账户数据
     */
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

    /**
     * 请求获取当前登录的用户账户数据
     */
    fun requestMyAccountData(
        callback: IServerCallback<MyBiliAccountData>
    ) = biliApiService.requestMyAccount().enqueue(callback) {
        it
    }

    /**
     * 请求获取用户收藏夹
     */
    fun requestUserFavorites(
        mid: Long, type: Int,
        callback: IServerCallback<BiliFavoriteListData>
    ) = biliApiService.requestUserFavorites(mid, type).enqueue(callback) {
        it
    }

    /**
     * 请求获取某个收藏夹详情
     */
    fun requestFavoriteDetails(
        id: Long, ps: Int, pn: Int,
        callback: IServerCallback<BiliFavoriteDetailsData>
    ) = biliApiService.requestFavoriteDetails(id, ps, pn).enqueue(callback) {
        it
    }

    /**
     * 账号登出
     */
    fun requestLogout(
        biliCSRF: String,
        callback: IServerCallback<JsonObject>
    ) = biliPassportService.requestLogout(
        biliCSRF
    ).enqueue(callback) { it }
}