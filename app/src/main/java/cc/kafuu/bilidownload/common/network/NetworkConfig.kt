package cc.kafuu.bilidownload.common.network

import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkConfig {
    private const val BILI_BASE_URL = "https://api.bilibili.com/"
    const val BILI_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 EdgiOS/46.3.30 Mobile/15E148 Safari/605.1.15"

    private var biliCookies: String? = null

    private val wbiManager = WbiManager() {
        return@WbiManager NetworkManager.biliWbiResponse.syncGetWbiKey()
    }

    private val biliInterceptor = BiliInterceptor(wbiManager) {
        return@BiliInterceptor biliCookies
    }

    val biliService: BiliApiService by lazy {
        Retrofit.Builder()
            .client(OkHttpClient.Builder().addInterceptor(biliInterceptor).build())
            .baseUrl(BILI_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BiliApiService::class.java)
    }
}