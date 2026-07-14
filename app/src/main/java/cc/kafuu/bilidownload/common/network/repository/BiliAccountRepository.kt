package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteDetailsData
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteListData
import cc.kafuu.bilidownload.common.network.model.BiliHistoryData
import cc.kafuu.bilidownload.common.network.model.BiliLikeListData
import cc.kafuu.bilidownload.common.network.model.BiliQrCodeData
import cc.kafuu.bilidownload.common.network.model.BiliQrCodePollData
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
     * 同步请求通过mid获取账户数据
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
        ).execute(onFailure) { _, data -> data }
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
                ).enqueue(callback) { _, data -> data }
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
    ) = biliApiService.requestMyAccount().enqueue(callback) { _, data -> data }

    /**
     * 请求获取用户收藏夹
     */
    fun requestUserFavorites(
        mid: Long, type: Int,
        callback: IServerCallback<BiliFavoriteListData>
    ) = biliApiService.requestUserFavorites(mid, type).enqueue(callback) { _, data -> data }

    /**
     * 请求获取某个收藏夹详情
     * @param ps 每页数量
     * @param pn 页码
     */
    fun requestFavoriteDetails(
        id: Long, ps: Int, pn: Int,
        callback: IServerCallback<BiliFavoriteDetailsData>
    ) = biliApiService.requestFavoriteDetails(id, ps, pn).enqueue(callback) { _, data -> data }

    /**
     * 同步请求获取某个收藏夹封面图片Url
     */
    fun syncRequestFavoriteCover(
        id: Long,
        onFailure: ((Int, Int, String) -> Unit)? = null
    ) = biliApiService.requestFavoriteInfo(id).execute(onFailure) { _, data -> data.cover }

    /**
     * 请求获取用户稿件历史记录
     */
    fun requestArchiveHistory(
        max: Long = 0,
        ps: Int = 20,
        business: String = "",
        viewAt: Long = 0,
        callback: IServerCallback<BiliHistoryData>
    ) {
        biliApiService
            .requestHistoryCursor(
                max = max,
                ps = ps,
                type = "archive",
                viewAt = viewAt,
                business = business
            )
            .enqueue(callback) { _, data -> data }
    }

    /**
     * 请求用户最近点赞的视频
     */
    fun requestUserRecentLikes(
        mid: Long,
        callback: IServerCallback<BiliLikeListData>
    ) = biliApiService.requestUserRecentLikes(mid).enqueue(callback) { _, data -> data }

    /**
     * 账号登出
     */
    fun requestLogout(
        biliCSRF: String,
        callback: IServerCallback<JsonObject>
    ) {
        biliPassportService
            .requestLogout(biliCSRF)
            .enqueue(callback) { _, data -> data }
    }

    /**
     * 请求登录二维码（有效期180s）
     */
    fun generateQrCode(callback: IServerCallback<BiliQrCodeData>) {
        biliPassportService.generateQrCode().enqueue(callback) { _, data -> data }
    }

    /**
     * 轮询验证码状态（如果用户确认登录则回调的二元组中，Second则为Cookies，否则都将为空字符串）
     */
    fun pollQrCode(key: String, callback: IServerCallback<Pair<BiliQrCodePollData, String>>) {
        biliPassportService.pollQrCode(key).enqueue(callback) { response, data ->
            val rawCookies = response.headers().values("Set-Cookie")
            val cookies = rawCookies.joinToString("; ") { it.substringBefore(";") }
            data to cookies
        }
    }
}
