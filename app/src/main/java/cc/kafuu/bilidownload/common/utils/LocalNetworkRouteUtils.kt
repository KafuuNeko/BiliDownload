package cc.kafuu.bilidownload.common.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import cc.kafuu.bilidownload.common.CommonLibs
import java.net.Inet6Address

/** 使用系统路由表判断 IPv6 地址是否属于非蜂窝、非 VPN 的本地网络路由。 */
object LocalNetworkRouteUtils {
    @Suppress("DEPRECATION")
    fun matches(address: Inet6Address): Boolean {
        val connectivityManager = CommonLibs.requireContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        // 需要同时检查非默认网络；例如 VPN 为默认网络时，局域网仍可能通过 Wi-Fi 可达。
        // getAllNetworks() 虽已弃用，但仍是执行一次性路由快照且覆盖这些网络的唯一 API。
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return@any false
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return@any false
            }

            connectivityManager.getLinkProperties(network)?.routes?.any { route ->
                !route.isDefaultRoute && route.matches(address)
            } == true
        }
    }
}
