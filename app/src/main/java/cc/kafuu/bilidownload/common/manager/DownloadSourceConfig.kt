package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.CommonLibs

object DownloadSourceConfig {
    private const val TAG = "DownloadSourceConfig"

    private const val FILE_CONFIG = "download_source_config"
    private const val KEY_DOWNLOAD_SOURCE = "download_source"

    /**
     * 下载源类型枚举
     */
    enum class DownloadSource(val displayName: String, val description: String) {
        AUTO("自动选择", "根据网络环境自动选择最快的CDN节点"),

        // Akamai CDN（海外特供，无国内节点，建议走代理使用）
        AKAMAI_HZ("upos-hz-mirrorakam.akamaized.net", "Akamai CDN (杭州) - 海外专用"),
        AKAMAI_SZ("upos-sz-mirrorakam.akamaized.net", "Akamai CDN (深圳) - 海外专用"),

        // 国内云 CDN（海外特供，有国内节点）
        MIRRORCOSO1("upos-sz-mirrocoso1.bilivideo.com", "海外加速节点 - 有国内节点"),
        ALL_HW("upos-tf-all-hw.bilivideo.com", "华为云节点 - 海外专用"),
        ALL_TX("upos-tf-all-tx.bilivideo.com", "腾讯云节点 - 海外专用"),
        ALL_WS("proxy-tf-all-ws.bilivideo.com", "网宿节点 - 海外专用"),

        // 国内云 CDN（海外特供，无国内节点）
        MIRRORALIOV("upos-sz-mirroraliov.bilivideo.com", "阿里云海外节点 - 无国内节点");

        companion object {
            fun fromDisplayName(displayName: String): DownloadSource {
                return DownloadSource.entries.find { it.displayName == displayName } ?: AUTO
            }
        }
    }

    /**
     * 当前选择的下载源
     */
    val downloadSourceLiveData = MutableLiveData<DownloadSource>(DownloadSource.AUTO)

    /**
     * 初始化配置，从本地存储读取
     */
    fun init() {
        val sourceName = getLocalSourceConfig()
        val source = DownloadSource.entries.find { it.displayName == sourceName }
            ?: DownloadSource.AUTO
        downloadSourceLiveData.value = source
    }

    /**
     * 设置下载源
     */
    fun setDownloadSource(source: DownloadSource) {
        downloadSourceLiveData.value = source
        saveLocalSourceConfig(source.displayName)
    }

    /**
     * 从本地存储读取配置
     */
    private fun getLocalSourceConfig(): String? {
        return CommonLibs.requireContext()
            .getSharedPreferences(FILE_CONFIG, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOAD_SOURCE, null)
    }

    /**
     * 保存配置到本地存储
     */
    private fun saveLocalSourceConfig(sourceName: String) {
        CommonLibs.requireContext()
            .getSharedPreferences(FILE_CONFIG, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOWNLOAD_SOURCE, sourceName)
            .apply()
    }

    /**
     * 根据配置选择合适的下载URL
     *
     * Bilibili URL 格式示例：
     * - https://upos-sz-gotcha01.bilivideo.com/xxx/xxx.m4s
     * - https://cn-gotcha01.bilivideo.com/xxx/xxx.m4s
     *
     * @param baseUrl 主要URL
     * @param backupUrl 备用URL列表（通常只有一个）
     * @return 选择的URL
     */
    fun selectDownloadUrl(baseUrl: String?, backupUrl: List<String>?): String? {
        val originalUrl = baseUrl ?: backupUrl?.firstOrNull() ?: return null
        Log.d(TAG, "selectDownloadUrl, origin: $baseUrl")
        return when (downloadSourceLiveData.value) {
            DownloadSource.AUTO -> originalUrl
            else -> {
                // 用户指定：替换 CDN 节点
                val targetCdn = downloadSourceLiveData.value?.cdnDomain ?: return originalUrl
                replaceCdnNode(originalUrl, targetCdn)
            }
        }.also {
            Log.d(TAG, "selectDownloadUrl, result: $baseUrl")
        }
    }

    /**
     * 替换 URL 中的 CDN 节点
     *
     * 例如：
     * - https://upos-sz-mirrocoso1.bilivideo.com/...
     * - https://cn-gotcha01.bilivideo.com/...
     * - https://upos-hz-mirrorakam.akamaized.net/...
     *
     * @param originalUrl 原始 URL
     * @param newCdn 完整的 CDN 节点域名（如 "upos-sz-mirrocoso1.bilivideo.com"）
     * @return 替换后的 URL
     */
    private fun replaceCdnNode(originalUrl: String, newCdn: String): String {
        // 匹配 :// 和第一个 / 之间的完整域名
        // 支持 .bilivideo.com 和 .akamaized.net 等各种域名
        val pattern = """(://|//)([^/?#]+)""".toRegex()

        val matchResult = pattern.find(originalUrl)
        if (matchResult != null) {
            val protocol = matchResult.groupValues[1]  // :// 或 //
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            return originalUrl.take(start) + protocol + newCdn + originalUrl.substring(end)
        }

        return originalUrl
    }

    /**
     * 获取完整的 CDN 域名
     */
    private val DownloadSource.cdnDomain: String?
        get() = when (this) {
            DownloadSource.AUTO -> null
            DownloadSource.AKAMAI_HZ -> "upos-hz-mirrorakam.akamaized.net"
            DownloadSource.AKAMAI_SZ -> "upos-sz-mirrorakam.akamaized.net"
            DownloadSource.MIRRORCOSO1 -> "upos-sz-mirrocoso1.bilivideo.com"
            DownloadSource.ALL_HW -> "upos-tf-all-hw.bilivideo.com"
            DownloadSource.ALL_TX -> "upos-tf-all-tx.bilivideo.com"
            DownloadSource.ALL_WS -> "proxy-tf-all-ws.bilivideo.com"
            DownloadSource.MIRRORALIOV -> "upos-sz-mirroraliov.bilivideo.com"
        }
}
