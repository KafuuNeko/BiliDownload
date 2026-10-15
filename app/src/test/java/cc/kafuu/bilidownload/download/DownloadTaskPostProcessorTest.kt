package cc.kafuu.bilidownload.download

import cc.kafuu.bilidownload.common.download.DownloadTaskPostProcessor
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class DownloadTaskPostProcessorTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun suspendedRegistration_keepsOutputNamesExclusiveUntilProcessingFinishes() = runBlocking {
        val processor = DownloadTaskPostProcessor(Dispatchers.Unconfined)
        val resumeRegistration = CompletableDeferred<Unit>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            processor.execute {
                val output = DownloadFileNameUtils.buildUniqueFile(files.root, "same-title", "mp4")
                // 模拟文件名已选择、Room 预登记尚未返回，文件还没有实际写入。
                resumeRegistration.await()
                output.writeText("first-task")
                output
            }
        }
        var secondEntered = false
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            processor.execute {
                secondEntered = true
                val output = DownloadFileNameUtils.buildUniqueFile(files.root, "same-title", "mp4")
                output.writeText("second-task")
                output
            }
        }

        try {
            assertFalse(secondEntered)
        } finally {
            resumeRegistration.complete(Unit)
        }
        val firstOutput = first.await()
        val secondOutput = second.await()
        assertNotEquals(firstOutput, secondOutput)
        assertEquals("first-task", firstOutput.readText())
        assertEquals("second-task", secondOutput.readText())
        // 第二个任务的失败清理只能删除自己的输出。
        assertTrue(secondOutput.delete())
        assertEquals("first-task", firstOutput.readText())
    }

    @Test
    fun cancellingWaitingTask_keepsCurrentOutputAndAllowsNextTask() = runBlocking {
        val processor = DownloadTaskPostProcessor(Dispatchers.Unconfined)
        val finishFirst = CompletableDeferred<Unit>()
        val output = files.newFile("current.mp4")
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            processor.execute {
                finishFirst.await()
                output.writeText("finished")
            }
        }
        var waitingTaskRan = false
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
            processor.execute { waitingTaskRan = true }
        }

        waiting.cancelAndJoin()
        assertFalse(waitingTaskRan)
        assertTrue(first.isActive)
        finishFirst.complete(Unit)
        first.join()
        assertEquals("finished", processor.execute { output.readText() })
    }

    @Test
    fun processingFailure_releasesLockForNextTask() = runBlocking {
        val processor = DownloadTaskPostProcessor(Dispatchers.Unconfined)
        val failure = runCatching {
            processor.execute { throw IOException("output unavailable") }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("next-task", processor.execute { "next-task" })
    }

    @Test
    fun cancellingActiveTask_finishesCleanupBeforeNextTask() = runBlocking {
        val processor = DownloadTaskPostProcessor(Dispatchers.Unconfined)
        val output = files.newFile("incomplete.mp4")
        val active = launch(start = CoroutineStart.UNDISPATCHED) {
            processor.execute {
                try {
                    awaitCancellation()
                } finally {
                    output.delete()
                }
            }
        }

        active.cancelAndJoin()
        assertFalse(processor.execute { output.exists() })
    }
}
