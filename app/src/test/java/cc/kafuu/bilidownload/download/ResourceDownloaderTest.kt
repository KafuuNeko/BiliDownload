package cc.kafuu.bilidownload.download

import cc.kafuu.bilidownload.common.download.DownloadCheckpoint
import cc.kafuu.bilidownload.common.download.DownloadFailure
import cc.kafuu.bilidownload.common.download.DownloadRetry
import cc.kafuu.bilidownload.common.download.DownloadRetryPolicy
import cc.kafuu.bilidownload.common.download.ResourceDownloadException
import cc.kafuu.bilidownload.common.download.ResourceDownloader
import cc.kafuu.bilidownload.download.DownloadTestServer.Companion.response
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 对真实 HTTP 响应与磁盘文件断言，验证恢复后的字节完整性和取消行为。 */
class ResourceDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
    private val retries = mutableListOf<DownloadRetry>()
    private val waits = mutableListOf<Long>()
    private val cache get() = File(temporary.root, "stream.part")
    private val output get() = File(temporary.root, "stream.m4s")
    private val etag = "ETag: \"version-one\"\r\n"

    @Test fun interruptedBody_resumesActualWrittenBytes() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response("abcd", length = 8, headers = etag)
            else response("efgh", 206, headers = etag + "Content-Range: bytes 4-7/8\r\n")
        }.use { server ->
            download(listOf(server.url))
            assertEquals("abcdefgh", output.readText())
            assertEquals("bytes=4-", server.requests[1].headers["range"])
            assertEquals("\"version-one\"", server.requests[1].headers["if-range"])
            assertEquals(1, retries.size)
        }
    }

    @Test fun brokenPrimary_switchesToBackupWithStrongValidator() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response("abcd", length = 8, headers = etag)
            else response("", 206, length = 4, headers = etag + "Content-Range: bytes 4-7/8\r\n")
        }.use { primary ->
            DownloadTestServer { response("efgh", 206, headers = etag + "Content-Range: bytes 4-7/8\r\n") }
                .use { backup ->
                    download(listOf(primary.url, backup.url))
                    assertEquals("abcdefgh", output.readText())
                    assertEquals("bytes=4-", backup.requests.single().headers["range"])
                }
        }
    }

    @Test fun switchingWithoutValidator_restartsInsteadOfCombiningDifferentFiles() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response("old!", length = 8)
            else response("", 206, length = 4, headers = "Content-Range: bytes 4-7/8\r\n")
        }.use { primary ->
            DownloadTestServer { response("new-file") }.use { backup ->
                download(listOf(primary.url, backup.url))
                assertEquals("new-file", output.readText())
                assertNull(backup.requests.single().headers["range"])
            }
        }
    }

    @Test fun ignoredRange_overwritesPrefix() = runBlocking {
        DownloadTestServer { response("complete", headers = etag) }.use { server ->
            seed(server.url)
            download(listOf(server.url))
            assertEquals("complete", output.readText())
            assertEquals("bytes=4-", server.requests.single().headers["range"])
        }
    }

    @Test fun legacyOrCorruptCheckpoint_restartsWithoutTrustingUnknownPrefix() = runBlocking {
        for (metadata in listOf<String?>(null, "source=\\uZZZZ")) {
            DownloadTestServer { response("verified") }.use { server ->
                cache.writeText("unknown-prefix")
                val checkpoint = File(cache.path + ".resume")
                checkpoint.delete()
                metadata?.let { checkpoint.writeText(it) }
                download(listOf(server.url))
                assertNull(server.requests.single().headers["range"])
                assertEquals("verified", output.readText())
                assertFalse(checkpoint.readText().contains(server.url))
            }
        }
    }

    @Test fun changedTotal_doesNotAppendEvenWhenValidatorMatches() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) {
                response("wrong", 206, headers = etag + "Content-Range: bytes 4-8/9\r\n")
            } else response("new-version")
        }.use { server ->
            seed(server.url)
            download(listOf(server.url))
            assertEquals("new-version", output.readText())
            assertNull(server.requests[1].headers["range"])
        }
    }

    @Test fun invalidRangeAndChangedValidator_restartSafely() = runBlocking {
        for (headers in listOf(
            etag + "Content-Range: bytes 2-5/8\r\n",
            "ETag: \"changed\"\r\nContent-Range: bytes 4-7/8\r\n"
        )) {
            val count = AtomicInteger()
            DownloadTestServer {
                if (count.getAndIncrement() == 0) response("xxxx", 206, headers = headers)
                else response("complete", headers = etag)
            }.use { server ->
                seed(server.url)
                download(listOf(server.url))
                assertEquals("complete", output.readText())
                assertNull(server.requests[1].headers["range"])
            }
        }
    }

    @Test fun rangeNotSatisfiable_resetsCacheWithinBudget() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response(code = 416)
            else response("complete")
        }.use { server ->
            seed(server.url)
            download(listOf(server.url))
            assertEquals("complete", output.readText())
            assertNull(server.requests[1].headers["range"])
        }
    }

    @Test fun unknownLength_acceptsNormalEofButNotEmptyMedia() = runBlocking {
        DownloadTestServer { response("stream", length = null) }.use { server ->
            download(listOf(server.url))
            assertEquals("stream", output.readText())
        }
        output.delete()
        DownloadTestServer { response(length = null) }.use { server ->
            val failure = failure { download(listOf(server.url)) }
            assertEquals(DownloadFailure.Kind.INVALID_RESPONSE, failure.failure.kind)
            assertFalse(output.exists())
            assertEquals(6, server.requests.size)
        }
    }

    @Test fun forbiddenPrimary_usesBackupBeforeRefreshing() = runBlocking {
        DownloadTestServer { response(code = 403) }.use { primary ->
            DownloadTestServer { response("backup") }.use { backup ->
                download(listOf(primary.url, backup.url), refresh = { error("Unexpected refresh") })
                assertEquals("backup", output.readText())
                assertEquals(1, primary.requests.size)
            }
        }
    }

    @Test fun exhaustedAddresses_refreshOnceWithoutChangingResourceSelection() = runBlocking {
        val refreshes = AtomicInteger()
        DownloadTestServer { response(code = 403) }.use { stale ->
            DownloadTestServer { response("fresh") }.use { fresh ->
                download(listOf(stale.url), refresh = {
                    refreshes.incrementAndGet()
                    listOf(fresh.url)
                })
                assertEquals("fresh", output.readText())
                assertEquals(1, refreshes.get())
            }
        }
    }

    @Test fun persistentFailures_haveSharedBoundedBudget() = runBlocking {
        val refreshes = AtomicInteger()
        DownloadTestServer { response(code = 503) }.use { server ->
            val error = failure {
                download(listOf(server.url), refresh = {
                    refreshes.incrementAndGet()
                    listOf(server.url)
                })
            }
            assertEquals(503, error.failure.httpCode)
            assertEquals(6, server.requests.size)
            assertEquals(1, refreshes.get())
            assertEquals(listOf(1, 2, 3, 4, 5), retries.map { it.attempt })
            assertFalse(output.exists())
        }
    }

    @Test fun failedRefresh_doesNotRefreshRepeatedly() = runBlocking {
        val refreshes = AtomicInteger()
        DownloadTestServer { response(code = 503) }.use { server ->
            failure {
                download(listOf(server.url), refresh = {
                    refreshes.incrementAndGet()
                    throw ResourceDownloadException(DownloadFailure(DownloadFailure.Kind.NETWORK), true)
                })
            }
            assertEquals(1, refreshes.get())
            assertEquals(5, server.requests.size)
        }
    }

    @Test fun rateLimit_honorsRetryAfterAndRejectsExcessiveWait() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response(code = 429, headers = "Retry-After: 7\r\n")
            else response("success")
        }.use { server ->
            download(listOf(server.url))
            assertEquals(7000L, waits.single())
            assertEquals("success", output.readText())
        }
        DownloadTestServer { response(code = 429, headers = "Retry-After: 120\r\n") }.use { server ->
            failure { download(listOf(server.url)) }
            assertEquals(1, server.requests.size)
        }
    }

    @Test fun storageFailure_isNotRetried() = runBlocking {
        assertTrue(cache.mkdir())
        DownloadTestServer { response("data") }.use { server ->
            val error = failure { download(listOf(server.url)) }
            assertEquals(DownloadFailure.Kind.STORAGE, error.failure.kind)
            assertTrue(retries.isEmpty())
            assertEquals(0, server.requests.size)
            assertFalse(output.exists())
        }
    }

    @Test fun cancellationDuringBackoff_keepsPrefixAndDoesNotRestart() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        DownloadTestServer { response("abcd", length = 8, headers = etag) }.use { server ->
            val job = async {
                downloader(wait = { waiting.complete(Unit); awaitCancellation() }).download(
                    listOf(server.url), cache, output, { error("Unexpected refresh") }, { _, _ -> }, { }
                )
            }
            withTimeout(5000) { waiting.await(); job.cancelAndJoin() }
            assertEquals("abcd", cache.readText())
            assertEquals(1, server.requests.size)
            assertFalse(output.exists())
        }
    }

    @Test fun cancellationDuringBodyRead_closesSocketImmediately() = runBlocking {
        val received = CompletableDeferred<Unit>()
        DownloadTestServer { response("abcd", length = 8, headers = etag, holdOpen = true) }.use { server ->
            val job = async {
                downloader().download(listOf(server.url), cache, output, { emptyList() },
                    { bytes, _ -> if (bytes == 4L) received.complete(Unit) }, { })
            }
            withTimeout(1500) { received.await(); job.cancelAndJoin() }
            assertTrue(server.peerClosed.await(1, TimeUnit.SECONDS))
            assertEquals("abcd", cache.readText())
            assertFalse(output.exists())
        }
    }

    @Test fun siblingFailure_cancelsOtherResourceRead() = runBlocking {
        val reading = CompletableDeferred<Unit>()
        DownloadTestServer { response("abcd", length = 8, headers = etag, holdOpen = true) }.use { slow ->
            DownloadTestServer { response(code = 400) }.use { failed ->
                val error = failure {
                    withTimeout(1500) {
                        coroutineScope {
                            async {
                                downloader().download(listOf(slow.url), cache, output, { emptyList() },
                                    { bytes, _ -> if (bytes == 4L) reading.complete(Unit) }, { })
                            }
                            reading.await()
                            async {
                                downloader().download(listOf(failed.url), File(temporary.root, "audio.part"),
                                    File(temporary.root, "audio.m4s"), { emptyList() }, { _, _ -> }, { })
                            }.await()
                        }
                    }
                }
                assertEquals(400, error.failure.httpCode)
                assertTrue(slow.peerClosed.await(1, TimeUnit.SECONDS))
                assertFalse(output.exists())
            }
        }
    }

    @Test fun cancellationDuringRefresh_doesNotIssueNewMediaRequest() = runBlocking {
        val refreshing = CompletableDeferred<Unit>()
        DownloadTestServer { response(code = 403) }.use { server ->
            val job = async {
                download(listOf(server.url), refresh = { refreshing.complete(Unit); awaitCancellation() })
            }
            withTimeout(1500) { refreshing.await(); job.cancelAndJoin() }
            assertEquals(1, server.requests.size)
            assertFalse(output.exists())
        }
    }

    private fun downloader(wait: suspend (Long) -> Unit = { waits.add(it); Unit }) = ResourceDownloader(
        client, { Request.Builder().url(it).build() }, DownloadRetryPolicy(jitterMillis = { 0 }), wait
    )

    private suspend fun download(urls: List<String>, refresh: suspend () -> List<String> = { urls }) {
        downloader().download(urls, cache, output, refresh, { _, _ -> }, { it?.let(retries::add) })
    }

    private fun seed(url: String) {
        cache.writeText("abcd")
        DownloadCheckpoint(cache).write(DownloadCheckpoint.Identity(
            DownloadCheckpoint.sourceKey(url), "\"version-one\"", 8
        ))
    }

    private suspend fun failure(block: suspend () -> Unit): ResourceDownloadException {
        try {
            block()
        } catch (error: ResourceDownloadException) {
            return error
        }
        throw AssertionError("Expected download failure")
    }
}
