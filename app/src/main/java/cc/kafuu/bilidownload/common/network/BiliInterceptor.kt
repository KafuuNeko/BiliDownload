package cc.kafuu.bilidownload.common.network

import android.util.Log
import cc.kafuu.bilidownload.common.network.manager.IWbiManager
import okhttp3.Interceptor
import okhttp3.Response

class BiliInterceptor(
    private val wbiManager: IWbiManager,
    private val getLatestCookies: () -> String?
) : Interceptor {
    private val TAG = "BiliInterceptor"
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalHttpUrl = originalRequest.url()
        val paramMap = HashMap<String, String>().apply {
            for (i in 0 until originalHttpUrl.querySize()) {
                put(
                    originalHttpUrl.queryParameterName(i),
                    originalHttpUrl.queryParameterValue(i)
                )
            }
        }
        val request = originalRequest.newBuilder().apply {
            wbiManager.generateSignature(originalHttpUrl.encodedPath(), paramMap)?.let {
                url(originalHttpUrl.newBuilder().query(it).build())
            }
            getLatestCookies()?.let { addHeader("Cookie", it) }
            addHeader("User-Agent", NetworkConfig.BILI_UA)
            addHeader("Accept", "application/json, text/plain, */*")
            addHeader("Accept-Language", "zh-CN,zh-Hans;q=0.9")
            addHeader("Origin", "https://m.bilibili.com")
            addHeader("Referer", "https://m.bilibili.com/")
        }.build()

        Log.d(TAG, "Ready request: $request")

        return chain.proceed(request).also {
            Log.d(TAG, "End of request: $it")
        }
    }

}
