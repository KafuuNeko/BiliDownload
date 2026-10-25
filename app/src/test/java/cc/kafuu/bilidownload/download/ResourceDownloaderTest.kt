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
    @get:Rule
    val temporary = TemporaryFolder()
    private val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
    private val retries = mutableListOf<DownloadRetry>()
    private val waits = mutableListOf<Long>()
    private val cache get() = File(temporary.root, "stream.part")
    private val output get() = File(temporary.root, "stream.m4s")
    private val etag = "ETag: \"version-one\"\r\n"

    @Test
    fun interruptedBody_resumesActualWrittenBytes() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response(body = "abcd", length = 8, headers = etag)
            else response(
                body = "efgh",
                code = 206,
                headers = etag + "Content-Range: bytes 4-7/8\r\n"
            )
        }.use { server ->
            download(urls = listOf(server.url))
            assertEquals("abcdefgh", output.readText())
            assertEquals("bytes=4-", server.requests[1].headers["range"])
            assertEquals("\"version-one\"", server.requests[1].headers["if-range"])
            assertEquals(1, retries.size)
        }
    }

    @Test
    fun brokenPrimary_switchesToBackupWithStrongValidator() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response(body = "abcd", length = 8, headers = etag)
            else response(
                body = "",
                code = 206,
                length = 4,
                headers = etag + "Content-Range: bytes 4-7/8\r\n"
            )
        }.use { primary ->
            DownloadTestServer {
                response(
                    body = "efgh",
                    code = 206,
                    headers = etag + "Content-Range: bytes 4-7/8\r\n"
                )
            }
                .use { backup ->
                    download(urls = listOf(primary.url, backup.url))
                    assertEquals("abcdefgh", output.readText())
                    assertEquals("bytes=4-", backup.requests.single().headers["range"])
                }
        }
    }

    @Test
    fun switchingWithoutValidator_restartsInsteadOfCombiningDifferentFiles() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response(body = "old!", length = 8)
            else response(
                body = "",
                code = 206,
                length = 4,
                headers = "Content-Range: bytes 4-7/8\r\n"
            )
        }.use { primary ->
            DownloadTestServer { response(body = "new-file") }.use { backup ->
                download(urls = listOf(primary.url, backup.url))
                assertEquals("new-file", output.readText())
                assertNull(backup.requests.single().headers["range"])
            }
        }
    }

    @Test
    fun ignoredRange_overwritesPrefix() = runBlocking {
        DownloadTestServer { response(body = "complete", headers = etag) }.use { server ->
            seed(url = server.url)
            download(urls = listOf(server.url))
            assertEquals("complete", output.readText())
            assertEquals("bytes=4-", server.requests.single().headers["range"])
        }
    }

    @Test
    fun legacyOrCorruptCheckpoint_restartsWithoutTrustingUnknownPrefix() = runBlocking {
        for (metadata in listOf<String?>(null, "source=\\uZZZZ")) {
            // 每种检查点使用独立成品，避免依赖平台对 renameTo 覆盖已有文件的差异。
            output.delete()
            DownloadTestServer { response(body = "verified") }.use { server ->
                cache.writeText("unknown-prefix")
                val checkpoint = File(cache.path + ".resume")
                checkpoint.delete()
                metadata?.let { checkpoint.writeText(it) }
                download(urls = listOf(server.url))
                assertNull(server.requests.single().headers["range"])
                assertEquals("verified", output.readText())
                assertFalse(checkpoint.readText().contains(server.url))
            }
        }
    }

    @Test
    fun changedTotal_doesNotAppendEvenWhenValidatorMatches() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) {
                response(
                    body = "wrong",
                    code = 206,
                    headers = etag + "Content-Range: bytes 4-8/9\r\n"
                )
            } else response(body = "new-version")
        }.use { server ->
            seed(url = server.url)
            download(urls = listOf(server.url))
            assertEquals("new-version", output.readText())
            assertNull(server.requests[1].headers["range"])
        }
    }

    @Test
    fun invalidRangeAndChangedValidator_restartSafely() = runBlocking {
        for (headers in listOf(
            etag + "Content-Range: bytes 2-5/8\r\n",
            "ETag: \"changed\"\r\nContent-Range: bytes 4-7/8\r\n"
        )) {
            // 此处验证范围与版本校验，不让上一组用例的成品参与发布。
            output.delete()
            val count = AtomicInteger()
            DownloadTestServer {
                if (count.getAndIncrement() == 0) response(
                    body = "xxxx",
                    code = 206,
                    headers = headers
                )
                else response(body = "complete", headers = etag)
            }.use { server ->
                seed(url = server.url)
                download(urls = listOf(server.url))
                assertEquals("complete", output.readText())
                assertNull(server.requests[1].headers["range"])
            }
        }
    }

    @Test
    fun rangeNotSatisfiable_resetsCacheWithinBudget() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response(code = 416)
            else response(body = "complete")
        }.use { server ->
            seed(url = server.url)
            download(urls = listOf(server.url))
            assertEquals("complete", output.readText())
            assertNull(server.requests[1].headers["range"])
        }
    }

    @Test
    fun unknownLength_acceptsNormalEofButNotEmptyMedia() = runBlocking {
        DownloadTestServer { response(body = "stream", length = null) }.use { server ->
            download(urls = listOf(server.url))
            assertEquals("stream", output.readText())
        }
        output.delete()
        DownloadTestServer { response(length = null) }.use { server ->
            val failure = failure { download(urls = listOf(server.url)) }
            assertEquals(DownloadFailure.Kind.INVALID_RESPONSE, failure.failure.kind)
            assertFalse(output.exists())
            assertEquals(6, server.requests.size)
        }
    }

    @Test
    fun forbiddenPrimary_usesBackupBeforeRefreshing() = runBlocking {
        DownloadTestServer { response(code = 403) }.use { primary ->
            DownloadTestServer { response(body = "backup") }.use { backup ->
                download(
                    urls = listOf(primary.url, backup.url),
                    refresh = { error("Unexpected refresh") })
                assertEquals("backup", output.readText())
                assertEquals(1, primary.requests.size)
            }
        }
    }

    /** CDN 未注册的 5xx 仍属于服务端失败，应轮换备用源且不能把错误正文当作成品。 */
    @Test
    fun unrecognizedServerError_usesBackupBeforeRefreshing() = runBlocking {
        for (code in listOf(567, 599)) {
            output.delete()
            retries.clear()
            DownloadTestServer { response(body = "server error", code = code) }.use { primary ->
                DownloadTestServer { response(body = "verified backup") }.use { backup ->
                    download(
                        urls = listOf(primary.url, backup.url),
                        refresh = { error("Unexpected refresh") })
                    assertEquals("verified backup", output.readText())
                    assertEquals(1, primary.requests.size)
                    assertEquals(1, backup.requests.size)
                    assertEquals(code, retries.single().failure.httpCode)
                }
            }
        }
    }

    /** 非标准服务端错误同样受既有恢复预算约束，不能无限重试或刷新。 */
    @Test
    fun unrecognizedServerError_stopsWithinSharedBudget() = runBlocking {
        val refreshes = AtomicInteger()
        DownloadTestServer { response(code = 567) }.use { server ->
            val error = failure {
                download(urls = listOf(server.url), refresh = {
                    refreshes.incrementAndGet()
                    listOf(server.url)
                })
            }
            assertEquals(567, error.failure.httpCode)
            assertEquals(6, server.requests.size)
            assertEquals(1, refreshes.get())
            assertEquals(listOf(1, 2, 3, 4, 5), retries.map { it.attempt })
            assertFalse(output.exists())
        }
    }

    @Test
    fun exhaustedAddresses_refreshOnceWithoutChangingResourceSelection() = runBlocking {
        val refreshes = AtomicInteger()
        DownloadTestServer { response(code = 403) }.use { stale ->
            DownloadTestServer { response(body = "fresh") }.use { fresh ->
                download(urls = listOf(stale.url), refresh = {
                    refreshes.incrementAndGet()
                    listOf(fresh.url)
                })
                assertEquals("fresh", output.readText())
                assertEquals(1, refreshes.get())
            }
        }
    }

    @Test
    fun persistentFailures_haveSharedBoundedBudget() = runBlocking {
        val refreshes = AtomicInteger()
        DownloadTestServer { response(code = 503) }.use { server ->
            val error = failure {
                download(urls = listOf(server.url), refresh = {
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

    @Test
    fun failedRefresh_doesNotRefreshRepeatedly() = runBlocking {
        val refreshes = AtomicInteger()
        DownloadTestServer { response(code = 503) }.use { server ->
            failure {
                download(urls = listOf(server.url), refresh = {
                    refreshes.incrementAndGet()
                    throw ResourceDownloadException(
                        failure = DownloadFailure(kind = DownloadFailure.Kind.NETWORK),
                        retryable = true
                    )
                })
            }
            assertEquals(1, refreshes.get())
            assertEquals(5, server.requests.size)
        }
    }

    @Test
    fun rateLimit_honorsRetryAfterAndRejectsExcessiveWait() = runBlocking {
        val count = AtomicInteger()
        DownloadTestServer {
            if (count.getAndIncrement() == 0) response(code = 429, headers = "Retry-After: 7\r\n")
            else response(body = "success")
        }.use { server ->
            download(urls = listOf(server.url))
            assertEquals(7000L, waits.single())
            assertEquals("success", output.readText())
        }
        DownloadTestServer {
            response(
                code = 429,
                headers = "Retry-After: 120\r\n"
            )
        }.use { server ->
            failure { download(urls = listOf(server.url)) }
            assertEquals(1, server.requests.size)
        }
    }

    @Test
    fun storageFailure_isNotRetried() = runBlocking {
        assertTrue(cache.mkdir())
        DownloadTestServer { response(body = "data") }.use { server ->
            val error = failure { download(urls = listOf(server.url)) }
            assertEquals(DownloadFailure.Kind.STORAGE, error.failure.kind)
            assertTrue(retries.isEmpty())
            // 目录 length() 在不同系统上可为零；失败可发生在请求前或首次落盘时。
            assertTrue(server.requests.size <= 1)
            assertFalse(output.exists())
        }
    }

    @Test
    fun cancellationDuringBackoff_keepsPrefixAndDoesNotRestart() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        DownloadTestServer { response(body = "abcd", length = 8, headers = etag) }.use { server ->
            val job = async {
                downloader(wait = {
                    waiting.complete(Unit)
                    awaitCancellation()
                }).download(
                    urls = listOf(server.url),
                    cache = cache,
                    output = output,
                    refreshUrls = { error("Unexpected refresh") },
                    onProgress = { _, _ -> },
                    onRetry = { }
                )
            }
            withTimeout(5000) {
                waiting.await()
                job.cancelAndJoin()
            }
            assertEquals("abcd", cache.readText())
            assertEquals(1, server.requests.size)
            assertFalse(output.exists())
        }
    }

    @Test
    fun cancellationDuringBodyRead_closesSocketImmediately() = runBlocking {
        val received = CompletableDeferred<Unit>()
        DownloadTestServer {
            response(
                body = "abcd",
                length = 8,
                headers = etag,
                holdOpen = true
            )
        }.use { server ->
            val job = async {
                downloader().download(
                    urls = listOf(server.url),
                    cache = cache,
                    output = output,
                    refreshUrls = { emptyList() },
                    onProgress = { bytes, _ -> if (bytes == 4L) received.complete(Unit) },
                    onRetry = { })
            }
            withTimeout(1500) {
                received.await()
                job.cancelAndJoin()
            }
            assertTrue(server.peerClosed.await(1, TimeUnit.SECONDS))
            assertEquals("abcd", cache.readText())
            assertFalse(output.exists())
        }
    }

    @Test
    fun siblingFailure_cancelsOtherResourceRead() = runBlocking {
        val reading = CompletableDeferred<Unit>()
        DownloadTestServer {
            response(
                body = "abcd",
                length = 8,
                headers = etag,
                holdOpen = true
            )
        }.use { slow ->
            DownloadTestServer { response(code = 400) }.use { failed ->
                val error = failure {
                    withTimeout(1500) {
                        coroutineScope {
                            async {
                                downloader().download(
                                    urls = listOf(slow.url),
                                    cache = cache,
                                    output = output,
                                    refreshUrls = { emptyList() },
                                    onProgress = { bytes, _ ->
                                        if (bytes == 4L) reading.complete(
                                            Unit
                                        )
                                    },
                                    onRetry = { })
                            }
                            reading.await()
                            async {
                                downloader().download(
                                    urls = listOf(failed.url),
                                    cache = File(temporary.root, "audio.part"),
                                    output = File(temporary.root, "audio.m4s"),
                                    refreshUrls = { emptyList() },
                                    onProgress = { _, _ -> },
                                    onRetry = { })
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

    @Test
    fun cancellationDuringRefresh_doesNotIssueNewMediaRequest() = runBlocking {
        val refreshing = CompletableDeferred<Unit>()
        DownloadTestServer { response(code = 403) }.use { server ->
            val job = async {
                download(
                    urls = listOf(server.url),
                    refresh = {
                        refreshing.complete(Unit)
                        awaitCancellation()
                    })
            }
            withTimeout(1500) {
                refreshing.await()
                job.cancelAndJoin()
            }
            assertEquals(1, server.requests.size)
            assertFalse(output.exists())
        }
    }

    private fun downloader(
        wait: suspend (Long) -> Unit = {
            waits.add(it)
            Unit
        }
    ) =
        ResourceDownloader(
            client = client,
            buildRequest = { Request.Builder().url(it).build() },
            retryPolicy = DownloadRetryPolicy(jitterMillis = { 0 }),
            waitBeforeRetry = wait
        )

    private suspend fun download(
        urls: List<String>,
        refresh: suspend () -> List<String> = { urls }
    ) {
        downloader().download(
            urls = urls,
            cache = cache,
            output = output,
            refreshUrls = refresh,
            onProgress = { _, _ -> },
            onRetry = { it?.let(retries::add) })
    }

    private fun seed(url: String) {
        cache.writeText("abcd")
        DownloadCheckpoint(part = cache).write(
            identity = DownloadCheckpoint.Identity(
                sourceKey = DownloadCheckpoint.sourceKey(url = url),
                etag = "\"version-one\"",
                total = 8
            )
        )
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
