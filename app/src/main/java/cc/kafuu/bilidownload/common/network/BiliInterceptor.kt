package cc.kafuu.bilidownload.common.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class BiliInterceptor(
    private val getLatestCookies: () -> String?
) : Interceptor {
    companion object {
        private const val TAG = "BiliInterceptor"
    }

    private var mCachedCookies: String? = null

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
        val request = chain.request().newBuilder().apply {
            // 获取最新cookie，若是不存在则访问网站获取默认cookie
            getLatestCookies()?.let {
                addHeader("Cookie", it)
            } ?: getDefaultCookiesByBili()?.let {
                addHeader("Cookie", it)
            }
            NetworkConfig.GENERAL_HEADERS.forEach { (key, value) -> addHeader(key, value) }
        }.build()

        Log.d(TAG, "Ready request: $request, cookie: ${request.headers()["Cookie"]}")

        return chain.proceed(request).also {
            Log.d(TAG, "End of request: $it")
        }
    }

    private fun getDefaultCookiesByBili(): String? {
        if (mCachedCookies == null) {
            val client = OkHttpClient.Builder().build() // 创建一个新的客户端实例
            val request = Request.Builder().url(NetworkConfig.BILI_URL).apply {
                NetworkConfig.GENERAL_HEADERS.forEach { (key, value) ->
                    addHeader(key, value)
                }
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                header("Accept-Encoding", "gzip, deflate, br, zstd")
            }.build()
            try {
                val response = client.newCall(request).execute() // 同步调用
                mCachedCookies = response.headers("Set-Cookie").joinToString("; ")
                Log.d(TAG, "Refreshed code: ${response.code()} default cookies: $mCachedCookies")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch cookies from ${NetworkConfig.BILI_URL}", e)
            }
        }
        return mCachedCookies
    }

}
