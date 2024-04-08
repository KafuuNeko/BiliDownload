package cc.kafuu.bilidownload.common.network

import cc.kafuu.bilidownload.common.manager.BiliManager
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkConfig {
    private const val BILI_BASE_URL = "https://api.bilibili.com/"

    val GENERAL_HEADERS = HashMap<String, String>().apply {
        put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 EdgiOS/46.3.30 Mobile/15E148 Safari/605.1.15")
        put("Accept", "application/json, text/plain, */*")
        put("Accept-Language", "zh-CN,zh-Hans;q=0.9")
        put("Origin", "https://m.bilibili.com")
        put("Referer", "https://m.bilibili.com/")
    }

    val DOWNLOAD_HEADERS = HashMap<String, String>(GENERAL_HEADERS).apply {
        put("Accept", "*/*")
        put("Accept-Language", "gzip, deflate, br")
        put("Connection", "keep-alive")
    }

    private val wbiManager = WbiManager() {
        return@WbiManager NetworkManager.biliWbiResponse.syncGetWbiKey()
    }

    private val biliInterceptor = BiliInterceptor(wbiManager) {
        return@BiliInterceptor BiliManager.cookies.value
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