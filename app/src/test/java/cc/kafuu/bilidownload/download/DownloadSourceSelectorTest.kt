package cc.kafuu.bilidownload.download

import cc.kafuu.bilidownload.common.download.DownloadSourceSelector
import cc.kafuu.bilidownload.download.DownloadTestServer.Companion.response
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** 探测以实际有效正文为准，失败地址仍保留为后续下载候选。 */
class DownloadSourceSelectorTest {
    private fun selector(timeoutMillis: Long = 3000) = DownloadSourceSelector(
        OkHttpClient(), { Request.Builder().url(it).build() }, timeoutMillis
    )

    @Test fun emptyAndTruncatedBodies_areNotRankedAsAvailable() = runBlocking {
        DownloadTestServer { response() }.use { empty ->
            DownloadTestServer { response(length = 1, code = 206) }.use { broken ->
                DownloadTestServer { response("valid") }.use { good ->
                    val urls = listOf(empty.url, broken.url, good.url)
                    val result = selector().rank(urls)
                    assertEquals(good.url, result.first())
                    assertEquals(urls.toSet(), result.toSet())
                    assertEquals("bytes=0-65535", good.requests.single().headers["range"])
                }
            }
        }
    }

    @Test fun unavailableCustomSource_fallsBackBeforeRetryingCustom() = runBlocking {
        DownloadTestServer { response(code = 403) }.use { custom ->
            val original = "https://cdn.example.test/video"
            assertEquals(listOf(original, custom.url), selector().rank(listOf(custom.url), listOf(original)))
        }
    }

    @Test fun ignoredRange_stopsAfterBoundedSample() = runBlocking {
        DownloadTestServer { response("x".repeat(65536), length = 1000000, holdOpen = true) }.use { server ->
            assertEquals(listOf(server.url), withTimeout(1500) { selector().rank(listOf(server.url)) })
            assertTrue(server.peerClosed.await(1, TimeUnit.SECONDS))
        }
    }

    @Test fun cancellation_closesProbeConnection() = runBlocking {
        DownloadTestServer { response("a", length = 100, holdOpen = true) }.use { server ->
            val job = async { selector().rank(listOf(server.url)) }
            withTimeout(1500) {
                while (server.requests.isEmpty()) delay(10)
                job.cancelAndJoin()
            }
            assertTrue(server.peerClosed.await(1, TimeUnit.SECONDS))
        }
    }
}
