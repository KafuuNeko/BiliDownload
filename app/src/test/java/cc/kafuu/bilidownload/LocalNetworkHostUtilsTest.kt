package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.utils.LocalNetworkHostUtils
import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkHostUtilsTest {
    @Test
    fun protectedLocalTargetsRequirePermission() {
        listOf(
            "10.0.0.1",
            "100.64.0.1",
            "169.254.1.2",
            "172.16.0.1",
            "192.168.1.1:8443",
            "printer.local",
            "nas",
            "[fe80::1]",
            "[fd00::1]",
            "239.1.2.3"
        ).forEach { host ->
            assertTrue(host, LocalNetworkHostUtils.requiresPermission(host))
        }
    }

    @Test
    fun publicAndLoopbackTargetsDoNotRequirePermission() {
        listOf(
            "8.8.8.8",
            "[2001:4860:4860::8888]",
            "localhost",
            "127.0.0.1",
            "[::1]",
            ""
        ).forEach { host ->
            assertFalse(host, LocalNetworkHostUtils.requiresPermission(host))
        }
    }

    @Test
    fun dottedHostsUseResolvedAddresses() {
        listOf(
            "nas.home.arpa",
            "router.lan",
            "cdn.example.com"
        ).forEach { host ->
            assertTrue(host, LocalNetworkHostUtils.requiresPermission(host) {
                arrayOf(InetAddress.getByName("192.168.50.20"))
            })
        }

        listOf(
            "upos-sz-mirrorali.bilivideo.com",
            "https://example.com"
        ).forEach { host ->
            assertFalse(host, LocalNetworkHostUtils.requiresPermission(host) {
                arrayOf(InetAddress.getByName("8.8.8.8"))
            })
        }
    }

    @Test
    fun anyProtectedResolvedAddressRequiresPermission() {
        assertTrue(
            LocalNetworkHostUtils.requiresPermission("split-dns.example.com") {
                arrayOf(
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("fd00::20")
                )
            }
        )
    }
}
