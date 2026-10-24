package cc.kafuu.bilidownload.common.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * 为 B 站请求补充当前 Cookie 和通用头，并按接口路径选择 User-Agent。
 *
 * 只处理请求头与安全诊断信息，不生成业务签名；响应体仍由调用方消费和关闭。
 *
 * @param getLatestCookies 每次拦截时读取账号凭据，缺失时尝试取得访客 Cookie。
 */
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

    // 只缓存该拦截器实例取得的访客 Cookie，不能用于替代每次读取的账号凭据。
    private var mCachedCookies: String? = null

    /**
     * 在当前请求上附加账号或访客 Cookie，并保留通用头的按路径 User-Agent 策略。
     *
     * - 在 OkHttp 请求线程运行，访客 Cookie 的首次获取可能同步访问移动站点。
     * - 日志只保留方法、路径和 HTTP 状态；完整 URL、请求头或响应对象可能暴露凭据。
     * - 真实请求与响应继续交给原调用链，不能在此提前消费业务响应体。
     *
     * @param chain 拦截器链，用于获取原始请求和继续执行下一个拦截器。
     * @return Response 返回经过处理的请求的响应。
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url().encodedPath()
        val request = original.newBuilder().apply {
            // 先读当前身份，避免切换账号后继续使用缓存；只有缺少账号 Cookie 才回退到访客 Cookie。
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

        // 不输出 Request/Response 本身，查询参数中可能带有临时鉴权签名。
        Log.d(TAG, "Request: ${original.method()} $path")

        return chain.proceed(request).also {
            Log.d(TAG, "Response: path=$path, http=${it.code()}")
        }
    }

    /** 按接口兼容规则选择 UA，其他请求沿用通用配置，不改写业务 URL 或签名参数。 */
    private fun pickUserAgent(path: String): String {
        val defaultUa = NetworkConfig.GENERAL_HEADERS["User-Agent"] ?: OKHTTP_USER_AGENT
        if (OKHTTP_UA_PATH_PREFIXES.any { path.startsWith(it) }) return OKHTTP_USER_AGENT
        if (BROWSER_UA_PATH_PREFIXES.any { path.startsWith(it) }) return defaultUa
        return defaultUa
    }

    /**
     * 同步获取并缓存访客 Cookie；网络 IO 失败时保留 null，允许后续请求再次尝试。
     *
     * 独立客户端不安装当前拦截器，避免获取 Cookie 时递归进入相同流程。
     * 请求只需要 Set-Cookie 头，仍须关闭整个响应体以释放连接；缓存内容不得写入日志。
     */
    private fun getDefaultCookiesByBili(): String? {
        // @formatter:off
        if (mCachedCookies == null) {
            val client = OkHttpClient.Builder().build() // 不复用带当前拦截器的业务客户端，避免递归。
            val request = Request.Builder().url(NetworkConfig.BILI_MOBILE_URL).apply {
                NetworkConfig.GENERAL_HEADERS.forEach { (key, value) ->
                    addHeader(key, value)
                }
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                header("Accept-Encoding", "gzip, deflate, br, zstd")
            }.build()
            try {
                // 不读取正文也要关闭响应，否则初始化请求会占用连接及底层资源。
                client.newCall(request).execute().use { response ->
                    mCachedCookies = response.headers("Set-Cookie").joinToString("; ")
                    Log.d(TAG, "Default cookies refreshed, http=${response.code()}")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch cookies from ${NetworkConfig.BILI_MOBILE_URL}", e)
            }
        }
        // @formatter:on
        return mCachedCookies
    }

}
