package cc.kafuu.bilidownload.common.network.manager

import cc.kafuu.bilidownload.common.network.IServerCallback
import cn.hutool.core.util.URLUtil
import cn.hutool.crypto.SecureUtil
import java.io.IOException
import java.util.StringJoiner

object WbiManager {
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    private var wbiCache: Pair<String, String>? = null
    private var wbiCacheTime: Long = 0

    private fun getMixinKey() = StringBuilder().apply {
        val wbiMap = wbiCache?.first + wbiCache?.second
        for (i in 0..31) {
            append(wbiMap[mixinKeyEncTab[i]])
        }
    }.toString()

    @Throws(IOException::class, IllegalStateException::class)
    private fun syncCheckUpdateWbi(callback: IServerCallback<Pair<String, String>>? = null): Boolean {
        // 若缓存失效则重置
        if (isCacheInvalid()) resetCache()

        if (wbiCache != null) {
            // 缓存还有效
            return true
        }

        return NetworkManager.biliWbiResponse.syncRequestWbiKey()?.let {
            updateCache(it.first, it.second)
            true
        } ?: false
    }

    private fun asyncCheckUpdateWbi(callback: IServerCallback<Pair<String, String>>) {
        // 若缓存失效则重置
        if (isCacheInvalid()) resetCache()

        if (wbiCache != null) {
            callback.onSuccess(0, 0, "Cache valid", wbiCache!!)
            return
        }

        NetworkManager.biliWbiResponse.requestWbiKey(object : IServerCallback<Pair<String, String>> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: Pair<String, String>
            ) {
                updateCache(data.first, data.second)
                callback.onSuccess(httpCode, code, message, data)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        })
    }

    private fun isCacheInvalid() = System.currentTimeMillis() - wbiCacheTime > 60000 * 5

    private fun resetCache() {
        wbiCache = null
    }

    private fun updateCache(imgKey: String, subKey: String) {
        wbiCache = Pair(imgKey, subKey)
        wbiCacheTime = System.currentTimeMillis()
    }

    private fun doGenerateSignature(paramMap: Map<String, Any>?): String {
        val params = StringJoiner("&")
        // 使用LinkedHashMap保持参数的插入顺序
        LinkedHashMap(paramMap).run {
            put("wts", System.currentTimeMillis() / 1000)
            //排序 + 拼接字符串
            entries.stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach { (key, value): Map.Entry<String, Any> ->
                    params.add("$key=${URLUtil.encode(value.toString())}")
                }
        }
        return "$params&w_rid=${SecureUtil.md5(params.toString() + getMixinKey())}"
    }

    /**
     * 根据给定的URL路径和参数映射生成带有WBI签名的请求参数字符串。
     *
     * @note 此函数可能会同步调用网络请求，应在非UI线程中执行。
     *
     * @throws IllegalStateException 如果请求WBI更新失败。
     *
     * @param paramMap 请求的参数映射，可以为null。
     * @return 带有WBI签名的请求参数字符串，如果不需要签名则返回null。
     */
    @Throws(IOException::class, IllegalStateException::class)
    fun syncGenerateSignature(paramMap: Map<String, Any>?): String {
        // 尝试请求更新Wbi
        if (!syncCheckUpdateWbi()) {
            throw IllegalStateException("WbiManager: Wbi request failed")
        }
        return doGenerateSignature(paramMap)
    }

    /**
     * 以异步的形式根据给定的URL路径和参数映射生成带有WBI签名的请求参数字符串。
     *
     * @throws IllegalStateException 如果请求WBI更新失败。
     *
     * @param paramMap 请求的参数映射，可以为null。
     * @return 带有WBI签名的请求参数字符串，如果不需要签名则返回null。
     */
    fun asyncGenerateSignature(paramMap: Map<String, Any>?, callback: IServerCallback<String>) {
        asyncCheckUpdateWbi(object : IServerCallback<Pair<String, String>> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: Pair<String, String>
            ) {
                callback.onSuccess(httpCode, code, message, doGenerateSignature(paramMap))
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        })
    }
}