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
        // B 站的 WAF/风控对浏览器 User-Agent 在 /x/web-interface/view 等接口上
        // 触发 412 风险拦截，但对 okhttp 客户端 User-Agent 不拦截；
        // 相反 /x/player/playurl 对 okhttp UA 会被拦截，需要保留浏览器 UA。
        // 因此按路径分别下发 User-Agent。
        private const val OKHTTP_USER_AGENT = "okhttp/4.12.0"
        private val OKHTTP_UA_PATH_PREFIXES = listOf(
            "/x/web-interface/view",
            "/x/web-interface/archive",
            "/x/v2/view",
        )
        private val BROWSER_UA_PATH_PREFIXES = listOf(
            "/x/player/playurl",
        )
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
        val original = chain.request()
        val path = original.url().encodedPath()
        val request = original.newBuilder().apply {
            // 获取最新cookie，若是不存在则访问网站获取默认cookie
            getLatestCookies()?.let {
                addHeader("Cookie", it)
            } ?: getDefaultCookiesByBili()?.let {
                addHeader("Cookie", it)
            }
            NetworkConfig.GENERAL_HEADERS.forEach { (key, value) ->
                if (key == "User-Agent") {
                    addHeader(key, pickUserAgent(path))
                } else {
                    addHeader(key, value)
                }
            }
        }.build()

        Log.d(TAG, "Ready request: $request, cookie: ${request.headers()["Cookie"]}")

        return chain.proceed(request).also {
            Log.d(TAG, "End of request: $it")
        }
    }

    private fun pickUserAgent(path: String): String {
        val defaultUa = NetworkConfig.GENERAL_HEADERS["User-Agent"] ?: OKHTTP_USER_AGENT
        if (OKHTTP_UA_PATH_PREFIXES.any { path.startsWith(it) }) return OKHTTP_USER_AGENT
        if (BROWSER_UA_PATH_PREFIXES.any { path.startsWith(it) }) return defaultUa
        return defaultUa
    }

    private fun getDefaultCookiesByBili(): String? {
        // @formatter:off
        if (mCachedCookies == null) {
            val client = OkHttpClient.Builder().build() // 创建一个新的客户端实例
            val request = Request.Builder().url(NetworkConfig.BILI_MOBILE_URL).apply {
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
                Log.e(TAG, "Failed to fetch cookies from ${NetworkConfig.BILI_MOBILE_URL}", e)
            }
        }
        // @formatter:on
        return mCachedCookies
    }

}
