package cc.kafuu.bilidownload.common.network.manager

import android.util.Log
import cn.hutool.core.util.URLUtil
import cn.hutool.crypto.SecureUtil
import java.util.StringJoiner

class WbiManager(private val getLatestWbiKey: () -> Pair<String, String>?) : IWbiManager {
    companion object {
        private val TAG = "BiliWbiRepository"

        // TODO: 需要WBI的API路径
        private val NEED_WBI_PATH = setOf(
            "/x/space/arc/search"
        )

        private val mixinKeyEncTab = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52
        )
    }

    private var wbiCache: Pair<String, String>? = null
    private var wbiCacheTime: Long = 0

    private fun getMixinKey() = StringBuilder().apply {
        val wbiMap = wbiCache?.first + wbiCache?.second
        for (i in 0..31) {
            append(wbiMap[mixinKeyEncTab[i]])
        }
    }.toString()

    private fun checkUpdateWbi(): Boolean {
        // 若缓存失效则重置
        if (isCacheInvalid()) resetCache()

        return if (wbiCache != null) {
            // 缓存还有效
            return true
        } else getLatestWbiKey()?.let {
            updateCache(it.first, it.second)
            true
        } ?: false
    }

    private fun isCacheInvalid() = System.currentTimeMillis() - wbiCacheTime > 60000 * 5

    private fun resetCache() {
        wbiCache = null
    }

    private fun updateCache(imgKey: String, subKey: String) {
        wbiCache = Pair(imgKey, subKey)
        wbiCacheTime = System.currentTimeMillis()
    }

    /**
     * 根据给定的URL路径和参数映射生成带有WBI签名的请求参数字符串。
     *
     * 此函数首先检查URL路径是否在需要WBI签名的路径列表中。如果不在此列表中，直接返回null。
     * 如果在列表中，则尝试通过调用`requestWbi`函数来进行WBI请求。如果WBI请求失败，也会返回null。
     *
     * @note 此函数可能会同步调用网络请求，应在非UI线程中执行。
     *
     * @throws IllegalStateException 如果请求WBI更新失败。
     *
     * @param urlPath 请求的URL路径。
     * @param params 请求的参数映射，可以为null。
     * @return 带有WBI签名的请求参数字符串，如果不需要签名则返回null。
     */
    override fun generateSignature(urlPath: String, params: Map<String, Any>?): String? {
        Log.d(TAG, "generateSignature: $urlPath, $params")

        if (!NEED_WBI_PATH.contains(urlPath)) {
            return null
        }

        // 尝试请求更新Wbi
        if (!checkUpdateWbi()) {
            throw IllegalStateException("WbiManager: Wbi request failed, path: $urlPath")
        }

        val param = StringJoiner("&")

        // 使用LinkedHashMap保持参数的插入顺序
        LinkedHashMap(params).run {
            put("wts", System.currentTimeMillis() / 1000)
            //排序 + 拼接字符串
            entries.stream()
                .sorted(java.util.Map.Entry.comparingByKey<String, Any>())
                .forEach { (key, value): Map.Entry<String, Any> ->
                    param.add("$key=${URLUtil.encode(value.toString())}")
                }
        }

        return "$param&w_rid=${SecureUtil.md5(param.toString() + getMixinKey())}"
    }
}