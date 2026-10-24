package cc.kafuu.bilidownload.download

import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.download.BatchRetryUseCase
import cc.kafuu.bilidownload.common.download.DownloadTaskRecoveryUseCase
import cc.kafuu.bilidownload.common.download.SynthesisRecoveryUseCase
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** 发布前验证恢复阶段、批量部分失败与源文件保留，不访问账号或正式数据库。 */
class DownloadRecoveryQaTest {
    @get:Rule val temporary = TemporaryFolder()

    /** 失败任务从其原阶段恢复，保留 taskId、分 P 身份及持久化状态。 */
    @Test fun failedTasks_resumeOnlyTheirOriginalPhase() = runBlocking {
        for ((status, expected) in listOf(
            TaskStatus.DOWNLOAD_FAILED to "download",
            TaskStatus.SYNTHESIS_FAILED to "synthesis",
            TaskStatus.PUBLISH_FAILED to "publish",
        )) {
            val task = task(1, status)
            val phases = mutableListOf<String>()
            val result = recovery(task, phases).execute(task.id, retryOnly = true)
            assertEquals(listOf(expected), phases)
            assertEquals(
                if (status == TaskStatus.DOWNLOAD_FAILED) DownloadTaskRecoveryUseCase.Result.HANDED_TO_DOWNLOADER
                else DownloadTaskRecoveryUseCase.Result.FINISHED,
                result,
            )
            assertEquals(status.code, task.status)
            assertEquals(1L, task.id)
            assertEquals(10L, task.biliCid)
        }
    }

    /** 专用重试命令只接受失败状态，拒绝完成、活动及未知状态。 */
    @Test fun retryOnly_rejectsCompletedActiveAndUnknownStates() = runBlocking {
        for (status in listOf(0, 1, 2, 3, 4, 999)) {
            val phases = mutableListOf<String>()
            val task = task(1, TaskStatus.PREPARE).apply { this.status = status }
            assertEquals(DownloadTaskRecoveryUseCase.Result.SKIPPED, recovery(task, phases).execute(1, true))
            assertTrue(phases.isEmpty())
        }
    }

    /** 普通恢复入口可继续被中断的活动阶段，不重新创建任务。 */
    @Test fun interruptedActiveTasks_resumeWithoutCreatingAnotherTask() = runBlocking {
        for ((status, expected) in listOf(
            TaskStatus.PREPARE to "download", TaskStatus.DOWNLOADING to "download",
            TaskStatus.SYNTHESIS to "synthesis", TaskStatus.PUBLISHING to "publish",
        )) {
            val phases = mutableListOf<String>()
            recovery(task(1, status), phases).execute(1, false)
            assertEquals(listOf(expected), phases)
        }
    }

    /** 已删除或仍在下载器中执行的任务不重复接管。 */
    @Test fun missingAndInFlightTasks_doNotResume() = runBlocking {
        val phases = mutableListOf<String>()
        assertEquals(DownloadTaskRecoveryUseCase.Result.SKIPPED, recovery(null, phases).execute(1, true))
        val useCase = recovery(task(1, TaskStatus.DOWNLOAD_FAILED), phases, downloading = true)
        assertEquals(DownloadTaskRecoveryUseCase.Result.SKIPPED, useCase.execute(1, true))
        assertTrue(phases.isEmpty())
    }

    /** 合成已推进到发布后发生异常，失败归属保持发布阶段。 */
    @Test fun publishFailureAfterSynthesis_keepsTheAdvancedFailurePhase() = runBlocking {
        val task = task(1, TaskStatus.SYNTHESIS_FAILED)
        var failedPhase: DownloadTaskRecoveryUseCase.Phase? = null
        val useCase = DownloadTaskRecoveryUseCase(
            download = { error("Unexpected download") },
            synthesize = { it.status = TaskStatus.PUBLISHING.code; throw IOException("publish") },
            publish = { error("Unexpected direct publish") },
            onFailure = { _, phase -> failedPhase = phase },
            loadTask = { task }, isDownloading = { false },
        )
        assertEquals(DownloadTaskRecoveryUseCase.Result.FINISHED, useCase.execute(1, true))
        assertEquals(DownloadTaskRecoveryUseCase.Phase.PUBLISH, failedPhase)
    }

    /** 取消向调用方传播，不写入业务失败状态。 */
    @Test fun recoveryCancellation_doesNotBecomeFailure() = runBlocking {
        var failureReported = false
        val useCase = DownloadTaskRecoveryUseCase(
            download = { throw CancellationException("cancelled") },
            synthesize = {}, publish = {}, onFailure = { _, _ -> failureReported = true },
            loadTask = { task(1, TaskStatus.DOWNLOAD_FAILED) }, isDownloading = { false },
        )
        expectCancellation { useCase.execute(1, true) }
        assertFalse(failureReported)
    }

    /** 下载器拒绝接管时返回跳过，允许服务释放本次登记。 */
    @Test fun rejectedDownload_doesNotRetainServiceOwnership() = runBlocking {
        val useCase = DownloadTaskRecoveryUseCase(
            download = { false }, synthesize = {}, publish = {}, onFailure = { _, _ -> },
            loadTask = { task(1, TaskStatus.DOWNLOAD_FAILED) }, isDownloading = { false },
        )
        assertEquals(DownloadTaskRecoveryUseCase.Result.SKIPPED, useCase.execute(1, true))
    }

    /** 批次去重并区分跳过与提交失败，不提前修改持久化任务状态。 */
    @Test fun batch_deduplicatesAndCountsPartialSubmissionWithoutMutatingTasks() = runBlocking {
        val tasks = (1L..6L).associateWith { task(it, TaskStatus.DOWNLOAD_FAILED) }
        tasks.getValue(2).status = TaskStatus.COMPLETED.code
        val submitted = mutableListOf<Long>()
        val progress = mutableListOf<BatchRetryUseCase.Progress>()
        // 混合完成、正在执行、提交异常和拒绝入队，验证部分成功的用户可见汇总。
        val useCase = BatchRetryUseCase(
            loadTask = { tasks[it] }, isInFlight = { it == 3L },
            submitRetry = { id ->
                if (id == 4L) throw IOException("submission failed")
                if (id == 5L) false else { submitted += id; true }
            },
        )
        val result = useCase.execute(listOf(1, 1, 2, 3, 4, 5, 6, 7)) { progress += it }
        assertEquals(BatchRetryUseCase.Result(2, 4, 1), result)
        assertEquals(listOf(1L, 6L), submitted)
        assertEquals((0..7).map { BatchRetryUseCase.Progress(it, 7) }, progress)
        assertEquals(TaskStatus.DOWNLOAD_FAILED.code, tasks.getValue(1).status)
    }

    /** 每次提交前重新读取资格，跳过在批次执行期间已经完成的任务。 */
    @Test fun batch_rechecksEachTaskAfterEarlierSubmission() = runBlocking {
        val tasks = (1L..2L).associateWith { task(it, TaskStatus.DOWNLOAD_FAILED) }
        val submitted = mutableListOf<Long>()
        val useCase = BatchRetryUseCase(
            loadTask = { tasks[it] }, isInFlight = { false },
            submitRetry = { id -> submitted += id; tasks.getValue(2).status = TaskStatus.COMPLETED.code; true },
        )
        assertEquals(BatchRetryUseCase.Result(1, 1, 0), useCase.execute(listOf(1, 2)))
        assertEquals(listOf(1L), submitted)
    }

    /** 取消后停止后续提交，保留此前已交给后台的任务身份。 */
    @Test fun batchCancellation_stopsFurtherSubmissions() = runBlocking {
        val submitted = mutableListOf<Long>()
        val useCase = BatchRetryUseCase(
            loadTask = { task(it, TaskStatus.DOWNLOAD_FAILED) }, isInFlight = { false },
            submitRetry = { id -> if (id == 2L) throw CancellationException("cancelled"); submitted += id; true },
        )
        expectCancellation { useCase.execute(listOf(1, 2, 3)) }
        assertEquals(listOf(1L), submitted)
    }

    /** 空批次不查询或提交任务，结果计数保持为零。 */
    @Test fun batchEmptyInput_isAnEmptySuccessfulSubmission() = runBlocking {
        val useCase = BatchRetryUseCase(
            loadTask = { error("Unexpected query") }, isInFlight = { false }, submitRetry = { false },
        )
        assertEquals(BatchRetryUseCase.Result(0, 0, 0), useCase.execute(emptyList()))
    }

    /** 源资源有效时才清理旧成品并进入合成，处理期间保留原输入内容。 */
    @Test fun synthesis_validatesSourcesBeforeCleaningAndPreservesOriginalInputs() = runBlocking {
        val task = task(1, TaskStatus.SYNTHESIS_FAILED)
        val resources = sources()
        val steps = mutableListOf<String>()
        synthesis(task, resources, steps).execute(task)
        assertEquals(listOf("clear", "save", "synthesize"), steps)
        assertEquals(TaskStatus.SYNTHESIS.code, task.status)
        resources.forEach { assertEquals("source", java.io.File(it.file).readText()) }
    }

    /** 缺少必需资源或文件为空时，不清理已有输出也不推进任务状态。 */
    @Test fun synthesis_missingOrEmptySource_doesNotDeleteExistingOutput() = runBlocking {
        val resources = sources()
        java.io.File(resources.last().file).writeText("")
        for (input in listOf(resources, resources.take(1))) {
            val task = task(1, TaskStatus.SYNTHESIS_FAILED)
            val steps = mutableListOf<String>()
            expectIoFailure { synthesis(task, input, steps).execute(task) }
            assertTrue(steps.isEmpty())
            assertEquals(TaskStatus.SYNTHESIS_FAILED.code, task.status)
        }
    }

    /** 清理旧成品失败时保留输入和失败状态，不继续合成。 */
    @Test fun synthesis_cleanupFailure_preservesFailureStateAndInputs() = runBlocking {
        val task = task(1, TaskStatus.SYNTHESIS_FAILED)
        val resources = sources()
        val steps = mutableListOf<String>()
        expectIoFailure { synthesis(task, resources, steps, clearResult = false).execute(task) }
        assertEquals(listOf("clear"), steps)
        assertEquals(TaskStatus.SYNTHESIS_FAILED.code, task.status)
        resources.forEach { assertEquals("source", java.io.File(it.file).readText()) }
    }

    /** 合成成功并删源后进程中断时，已提交尺寸的成品应继续发布，不能因源缺失永久失败。 */
    @Test fun synthesis_committedOutputResumesAfterSourceDeletion() = runBlocking {
        val task = task(1, TaskStatus.SYNTHESIS)
        val output = temporary.newFile().apply { writeText("complete mixed media") }
        val resource = DownloadResourceEntity(
            taskId = 1, type = DownloadResourceType.MIXED, name = "QA mixed", mimeType = "video/mp4",
            storageSizeBytes = output.length(), creationTime = 0, file = output.path,
        )
        val steps = mutableListOf<String>()
        synthesis(task, listOf(resource), steps).execute(task)
        assertEquals(listOf("finish"), steps)
        assertEquals("complete mixed media", output.readText())
    }

    /** 预登记成品或被截断的成品不能冒充完成，仍应使用有效源重新合成。 */
    @Test fun synthesis_incompleteOutputDoesNotSkipSynthesis() = runBlocking {
        val sources = sources()
        val output = temporary.newFile().apply { writeText("partial") }
        for (size in listOf(0L, output.length() + 1)) {
            val resource = DownloadResourceEntity(
                taskId = 1, type = DownloadResourceType.MIXED, name = "QA partial", mimeType = "video/mp4",
                storageSizeBytes = size, creationTime = 0, file = output.path,
            )
            val steps = mutableListOf<String>()
            val task = task(1, TaskStatus.SYNTHESIS_FAILED)
            synthesis(task, sources + resource, steps).execute(task)
            assertEquals(listOf("clear", "save", "synthesize"), steps)
        }
    }

    private fun recovery(
        task: DownloadTaskEntity?, phases: MutableList<String>, downloading: Boolean = false,
    ) = DownloadTaskRecoveryUseCase(
        download = { phases += "download"; true }, synthesize = { phases += "synthesis" },
        publish = { phases += "publish" }, onFailure = { _, _ -> error("Unexpected failure") },
        loadTask = { task }, isDownloading = { downloading },
    )

    private fun synthesis(
        task: DownloadTaskEntity, resources: List<DownloadResourceEntity>, steps: MutableList<String>,
        clearResult: Boolean = true,
    ) = SynthesisRecoveryUseCase(
        runSynthesis = { steps += "synthesize" },
        finishCompletedSynthesis = { steps += "finish" },
        loadDash = { listOf(
            DownloadDashEntity(80, task.id, 7, DashType.VIDEO, "video/mp4", "avc1"),
            DownloadDashEntity(30280, task.id, 0, DashType.AUDIO, "audio/mp4", "mp4a"),
        ) },
        loadResources = { resources }, clearMixedResources = { steps += "clear"; clearResult },
        saveTask = { steps += "save" },
    )

    private fun sources() = listOf(DownloadResourceType.VIDEO, DownloadResourceType.AUDIO).map { type ->
        val file = temporary.newFile().apply { writeText("source") }
        DownloadResourceEntity(
            taskId = 1, type = type, name = "QA source", mimeType = "video/mp4",
            storageSizeBytes = file.length(), creationTime = 0, file = file.path,
        )
    }

    private fun task(id: Long, status: TaskStatus) = DownloadTaskEntity(
        id = id, biliBvid = "BV1xx411c7mD", biliCid = 10, status = status.code,
    )

    private suspend fun expectCancellation(block: suspend () -> Unit) {
        try { block() } catch (_: CancellationException) { return }
        throw AssertionError("Expected cancellation")
    }

    private suspend fun expectIoFailure(block: suspend () -> Unit) {
        try { block() } catch (_: IOException) { return }
        throw AssertionError("Expected source or cleanup failure")
    }
}
