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

    /**
     * 拦截发出的网络请求，对请求进行处理，添加必要的签名、Cookie和HTTP头部信息。
     *
     * 此拦截器主要完成以下几项任务：
     * 1. 从原始请求的URL中提取查询参数，并使用这些参数生成请求的签名。
     * 2. 如果存在最新的Cookie，将其添加到请求头中。
     * 3. 添加标准的HTTP头部信息，如`User-Agent`、`Accept`、`Accept-Language`、`Origin`和`Referer`。
     *
     * @param chain 拦截器链，用于获取原始请求和继续执行下一个拦截器。
     * @return Response 返回经过处理的请求的响应。
     */
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
            NetworkConfig.GENERAL_HEADERS.forEach { (key, value) -> addHeader(key, value) }
        }.build()

        Log.d(TAG, "Ready request: $request")

        return chain.proceed(request).also {
            Log.d(TAG, "End of request: $it")
        }
    }

}
