package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.download.DownloadTaskRequestRegistry
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.service.DownloadServiceTaskRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class DownloadServiceTaskRegistryTest {
    private val requests = DownloadTaskRequestRegistry()
    private val registry = DownloadServiceTaskRegistry(requests)

    @Test
    fun duplicateTask_isRegisteredOnceAndUsesLatestStartId() {
        val request = checkNotNull(requests.reserve(1L))
        val assignment = checkNotNull(registry.tryRegister(1L, request.token, 1))

        assertNull(registry.tryRegister(1L, request.token, 2))
        assertSame(assignment, registry.find(1L))
        assertEquals(2, registry.finish(assignment))
        assertNull(registry.finish(assignment))
        assertTrue(requests.taskIds.value.isEmpty())
    }

    @Test
    fun finish_returnsStartIdOnlyAfterAllTasksFinish() {
        val first = register(1L, 1)
        val second = register(2L, 2)

        assertNull(registry.finish(second))
        assertNull(registry.stopIdIfIdle())
        assertEquals(setOf(1L), requests.taskIds.value)
        assertEquals(2, registry.finish(first))
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun concurrentDuplicateTask_isRegisteredOnce() {
        val request = checkNotNull(requests.reserve(1L))
        val executor = Executors.newFixedThreadPool(8)

        try {
            val results = executor.invokeAll(
                (1..32).map { startId ->
                    Callable { registry.tryRegister(1L, request.token, startId) }
                }
            ).mapNotNull { it.get() }

            assertEquals(1, results.size)
            assertEquals(32, registry.finish(results.single()))
            assertFalse(requests.contains(1L))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun retryOnRelease_isAcceptedBeforeOldFinishReturns() = runBlocking {
        val old = register(1L, 1)
        var retry: DownloadServiceTaskRegistry.Assignment? = null

        // 直接在在途状态释放时提交并交付新 Intent，覆盖旧清理尚未返回的竞争窗口。
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            requests.taskIds.drop(1).first { 1L !in it }
            retry = register(1L, 2)
        }
        val stopId = registry.finish(old)
        observer.join()

        assertNotNull(retry)
        assertSame(retry, registry.find(1L))
        assertEquals(setOf(1L), requests.taskIds.value)
        assertNull(stopId)
        assertEquals(2, registry.finish(checkNotNull(retry)))
        assertNotNull(requests.reserve(1L))
    }

    @Test
    fun lateFinish_doesNotReleaseNewAssignment() {
        val old = register(1L, 1)
        assertEquals(1, registry.finish(old))
        val retry = register(1L, 2)

        assertNull(registry.finish(old))
        assertFalse(requests.release(old.request))
        assertSame(retry, registry.find(1L))
        assertEquals(retry.request, requests.find(1L, retry.request.token))
        assertEquals(2, registry.finish(retry))
    }

    @Test
    fun staleIntent_preservesPendingRetryAndLatestStartId() {
        val old = register(1L, 1)
        registry.finish(old)
        val retryRequest = checkNotNull(requests.reserve(1L))

        assertNull(registry.tryRegister(1L, old.request.token, 2))
        assertEquals(retryRequest, requests.find(1L, retryRequest.token))
        val retry = checkNotNull(registry.tryRegister(1L, retryRequest.token, 3))
        assertEquals(3, registry.finish(retry))
    }

    @Test
    fun restoredIntent_reservesIdentityWithoutInMemoryRequest() {
        val assignment = checkNotNull(registry.tryRegister(1L, token = 99L, startId = 1))

        assertEquals(assignment.request, requests.find(1L, assignment.request.token))
        assertNull(requests.reserve(1L))
        assertEquals(1, registry.finish(assignment))
        assertFalse(requests.contains(1L))
    }

    @Test
    fun events_requireCurrentAssignmentAndOriginalEntity() {
        val task = DownloadTaskEntity(id = 1L, biliBvid = "test-video", biliCid = 1L)
        val old = register(1L, 1).apply { this.task = task }
        assertTrue(registry.owns(old, task))
        assertFalse(registry.owns(old, task.copy()))
        registry.finish(old)
        val retryTask = task.copy()
        val retry = register(1L, 2).apply { this.task = retryTask }

        assertFalse(registry.owns(old, task))
        assertFalse(registry.owns(retry, task))
        assertTrue(registry.owns(retry, retryTask))
    }

    private fun register(taskId: Long, startId: Int): DownloadServiceTaskRegistry.Assignment {
        val request = checkNotNull(requests.reserve(taskId))
        return checkNotNull(registry.tryRegister(taskId, request.token, startId))
    }
}
