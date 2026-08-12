package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.service.DownloadServiceTaskRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class DownloadServiceTaskRegistryTest {
    @Test
    fun duplicateTask_isRegisteredOnceAndUsesLatestStartId() {
        val registry = DownloadServiceTaskRegistry()

        assertTrue(registry.tryRegister(taskId = 1L, startId = 1))
        assertFalse(registry.tryRegister(taskId = 1L, startId = 2))
        assertEquals(2, registry.finish(taskId = 1L))
        assertNull(registry.finish(taskId = 1L))
    }

    @Test
    fun finish_returnsStartIdOnlyAfterAllTasksFinish() {
        val registry = DownloadServiceTaskRegistry()

        assertTrue(registry.tryRegister(taskId = 1L, startId = 1))
        assertTrue(registry.tryRegister(taskId = 2L, startId = 2))
        assertNull(registry.finish(taskId = 2L))
        assertEquals(2, registry.finish(taskId = 1L))
    }

    @Test
    fun concurrentDuplicateTask_isRegisteredOnce() {
        val registry = DownloadServiceTaskRegistry()
        val executor = Executors.newFixedThreadPool(8)

        try {
            val results = executor.invokeAll(
                (1..32).map { startId ->
                    Callable {
                        registry.tryRegister(taskId = 1L, startId = startId)
                    }
                }
            )

            assertEquals(1, results.count { it.get() })
            assertEquals(32, registry.finish(taskId = 1L))
        } finally {
            executor.shutdownNow()
        }
    }
}
