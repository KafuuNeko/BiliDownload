package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.model.bili.VideoStats
import cc.kafuu.bilidownload.common.network.repository.BiliVideoStatsRepository
import cc.kafuu.bilidownload.common.network.repository.BiliVideoStatsRepository.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 用可控请求验证跨页面共享、取消、缓存失效与限流，不依赖真实网络或账号。 */
class BiliVideoStatsRepositoryTest {
    private val success = Result.Success(VideoStats(100, 20, 30))

    @Test
    fun sharedRequest_survivesOneSubscriberLeaving() = runBlocking {
        withTimeout(5000) {
            val started = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Result>()
            val calls = AtomicInteger()
            val repository = repository {
                calls.incrementAndGet()
                started.complete(Unit)
                response.await()
            }
            val first = async(start = CoroutineStart.UNDISPATCHED) { repository.getStats("one") }
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.getStats("one") }
            started.await()
            first.cancelAndJoin()
            response.complete(success)
            assertEquals(success, second.await())
            assertEquals(1, calls.get())
            assertEquals(success.stats, repository.getCached("one"))
        }
    }

    @Test
    fun lastSubscriberLeaving_cancelsWorkWithoutCachingFailure() = runBlocking {
        withTimeout(5000) {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val repository = repository {
                if (calls.incrementAndGet() == 1) {
                    try {
                        started.complete(Unit)
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
                success
            }
            val first = async { repository.getStats("one") }
            started.await()
            first.cancelAndJoin()
            cancelled.await()
            assertNull(repository.getCached("one"))
            assertEquals(success, repository.getStats("one"))
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun invalidation_rejectsLateResponseFromOldAccount() = runBlocking {
        withTimeout(5000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val returned = CompletableDeferred<Unit>()
            val repository = repository {
                withContext(NonCancellable) {
                    started.complete(Unit)
                    release.await()
                    returned.complete(Unit)
                }
                success
            }
            val old = async { repository.getStats("one") }
            started.await()
            repository.invalidate()
            release.complete(Unit)
            returned.await()
            old.join()
            assertTrue(old.isCancelled)
            assertNull(repository.getCached("one"))
        }
    }

    @Test
    fun cache_expiresAndEvictsLeastRecentlyUsed() = runBlocking {
        val now = AtomicLong()
        val calls = AtomicInteger()
        val repository = BiliVideoStatsRepository(
            requestStats = { calls.incrementAndGet(); success },
            nowMillis = now::get, requestIntervalMillis = 0, cacheTtlMillis = 100,
            maxCacheEntries = 2,
        )
        repository.getStats("one")
        repository.getStats("two")
        repository.getCached("one")
        repository.getStats("three")
        assertNull(repository.getCached("two"))
        assertEquals(success, repository.getStats("one"))
        assertEquals(3, calls.get())
        now.set(100)
        assertNull(repository.getCached("one"))
        assertEquals(success, repository.getStats("one"))
        assertEquals(4, calls.get())
    }

    @Test
    fun failureCacheAndGlobalCooldown_preventRetryStorms() = runBlocking {
        val now = AtomicLong()
        val calls = AtomicInteger()
        var response: Result = Result.Failure(apiCode = -352)
        val repository = BiliVideoStatsRepository(
            requestStats = { calls.incrementAndGet(); response },
            nowMillis = now::get, requestIntervalMillis = 0,
            failureTtlMillis = 10, cooldownMillis = 100,
        )
        assertEquals(response, repository.getStats("one"))
        now.set(20)
        assertTrue(repository.getStats("two") is Result.Failure)
        assertEquals(1, calls.get())
        now.set(100)
        response = Result.Failure(httpCode = 503)
        assertEquals(response, repository.getStats("two"))
        assertEquals(response, repository.getStats("two"))
        assertEquals(2, calls.get())
        now.set(110)
        response = success
        assertEquals(success, repository.getStats("two"))
        assertEquals(3, calls.get())
    }

    @Test
    fun concurrentRequests_neverExceedTwo() = runBlocking {
        withTimeout(5000) {
            val entered = Channel<Unit>(Channel.UNLIMITED)
            val release = Channel<Unit>(Channel.UNLIMITED)
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val repository = repository {
                val count = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                entered.send(Unit)
                try {
                    release.receive()
                    success
                } finally {
                    active.decrementAndGet()
                }
            }
            val requests = (1..4).map { async { repository.getStats(it.toString()) } }
            repeat(2) { entered.receive() }
            repeat(4) { release.send(Unit) }
            assertEquals(List(4) { success }, requests.awaitAll())
            assertEquals(2, maximum.get())
        }
    }

    private fun repository(request: suspend (String) -> Result) =
        BiliVideoStatsRepository(requestStats = request, requestIntervalMillis = 0)
}
