package cc.kafuu.bilidownload.common.utils

import okhttp3.HttpUrl
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** 判断自定义下载 Host 是否可能落在 Android 17 受保护的本地网络范围。 */
object LocalNetworkHostUtils {
    fun requiresPermission(
        rawHost: String,
        addressResolver: (String) -> Array<InetAddress> = InetAddress::getAllByName
    ): Boolean {
        val value = rawHost.trim().trimEnd('/')
        if (value.isBlank() || value.any { it.isWhitespace() }) return false

        val url = if (value.contains("://")) value else "https://$value"
        val host = HttpUrl.parse(url)?.host()?.lowercase() ?: return false

        if (host == "localhost" || host == "::1") return false
        if (host.endsWith(".local")) return true

        // 单标签主机名通常依赖局域网 DNS；在解析前先保守申请权限。
        if (!host.contains('.') && !host.contains(':')) return true

        return runCatching { addressResolver(host).any(::isProtectedAddress) }
            .getOrDefault(false)
    }

    private fun isProtectedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return false
        if (address.isSiteLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress) {
            return true
        }

        val bytes = address.address
        return when (address) {
            is Inet4Address -> {
                val first = bytes[0].toInt() and 0xFF
                val second = bytes[1].toInt() and 0xFF
                (first == 100 && second in 64..127) || bytes.all { (it.toInt() and 0xFF) == 255 }
            }

            is Inet6Address -> (bytes.first().toInt() and 0xFE) == 0xFC
            else -> false
        }
    }
}
