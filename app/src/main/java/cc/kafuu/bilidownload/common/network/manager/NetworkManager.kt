package cc.kafuu.bilidownload.common.network.manager

import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.network.BiliInterceptor
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.repository.BiliAccountRepository
import cc.kafuu.bilidownload.common.network.repository.BiliSearchRepository
import cc.kafuu.bilidownload.common.network.repository.BiliVideoRepository
import cc.kafuu.bilidownload.common.network.repository.BiliWbiRepository
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import cc.kafuu.bilidownload.common.network.service.BiliPassportService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object NetworkManager {
    private val biliInterceptor = BiliInterceptor {
        return@BiliInterceptor AccountManager.cookiesLiveData.value
    }

    private val biliService: BiliApiService by lazy {
        createService(NetworkConfig.BILI_API_URL, BiliApiService::class.java)
    }

    private val biliPassportService: BiliPassportService by lazy {
        createService(NetworkConfig.BILI_PASSPORT_URL, BiliPassportService::class.java)
    }

    private fun <T> createService(baseUrl: String, serviceClass: Class<T>) = Retrofit.Builder()
        .client(OkHttpClient.Builder().addInterceptor(biliInterceptor).build())
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(serviceClass)

    val biliWbiResponse = BiliWbiRepository(biliService)

    val biliVideoRepository by lazy { BiliVideoRepository(biliService) }

    val biliAccountRepository by lazy { BiliAccountRepository(biliService, biliPassportService) }

    val biliSearchRepository by lazy { BiliSearchRepository(biliService) }
}