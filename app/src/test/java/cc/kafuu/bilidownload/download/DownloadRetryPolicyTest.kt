package cc.kafuu.bilidownload.download

import cc.kafuu.bilidownload.common.download.DownloadFailure
import cc.kafuu.bilidownload.common.download.DownloadRetryPolicy
import cc.kafuu.bilidownload.common.download.ResourceDownloadException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 限流等待不能被退避缩短，预算和溢出值不能形成无限恢复。 */
class DownloadRetryPolicyTest {
    @Test fun retryAfter_supportsSecondsAndHttpDate() {
        assertEquals(7000L, DownloadRetryPolicy.retryAfterMillis("7"))
        assertEquals(7000L, DownloadRetryPolicy.retryAfterMillis("Thu, 01 Jan 1970 00:00:07 GMT", 0))
        assertEquals(0L, DownloadRetryPolicy.retryAfterMillis("invalid"))
        assertEquals(Long.MAX_VALUE, DownloadRetryPolicy.retryAfterMillis("9999999999999999999999"))
    }

    @Test fun exponentialBackoff_hasSharedBudgetAndHonorsServerWait() {
        val policy = DownloadRetryPolicy(jitterMillis = { 0 })
        val network = ResourceDownloadException(DownloadFailure(DownloadFailure.Kind.NETWORK), true)
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L), (1..5).map { policy.delayMillis(network, it) })
        assertNull(policy.delayMillis(network, 6))
        assertNull(policy.delayMillis(ResourceDownloadException(DownloadFailure(DownloadFailure.Kind.STORAGE)), 1))
        assertEquals(7000L, policy.delayMillis(ResourceDownloadException(network.failure, true, 7000), 1))
        assertNull(policy.delayMillis(ResourceDownloadException(network.failure, true, 120000), 1))
    }
}
