package cc.kafuu.bilidownload

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 通过真实 Repository 和 Room 事务验证下载组合去重；仅清理本用例创建的虚构测试记录。 */
@RunWith(AndroidJUnit4::class)
class DownloadTaskCreationTest {
    private val testBvid = "issue-76-${UUID.randomUUID()}"
    private val database get() = CommonLibs.requireAppDatabase()
    private val video = BiliDashModel(
        type = DashType.VIDEO,
        dashId = 80L,
        codecId = 7L,
        mimeType = "video/mp4",
        codecs = "avc1.test",
    )
    private val audio = BiliDashModel(
        type = DashType.AUDIO,
        dashId = 30280L,
        codecId = 0L,
        mimeType = "audio/mp4",
        codecs = "mp4a.test",
    )
    private val resources get() = listOf(video, audio)

    /** 测试不启动下载服务、不创建媒体文件，清理限定在每个用例独有的稿件前缀内。 */
    @After
    fun cleanUp() = runBlocking {
        database.withTransaction {
            queryTestTasks().forEach { task ->
                database.downloadDashDao().deleteTaskByTaskId(taskEntityId = task.id)
                database.downloadTaskDao().deleteTaskByTaskId(taskEntityId = task.id)
            }
        }
    }

    /** 同分集换用另一编码时保留独立任务和原始 DASH 选择。 */
    @Test
    fun createDifferentCodecForSamePart() = runBlocking {
        val first = createTask(resources = resources)
        val second = createTask(resources = listOf(video.copy(codecId = 12L), audio))

        assertNotEquals(first.id, second.id)
        assertEquals(first.id, first.groupId)
        assertEquals(second.id, second.groupId)
        val firstVideo =
            DownloadRepository.queryDashList(entity = first).single { it.type == DashType.VIDEO }
        val secondVideo =
            DownloadRepository.queryDashList(entity = second).single { it.type == DashType.VIDEO }
        assertEquals(7L, firstVideo.codecId)
        assertEquals(12L, secondVideo.codecId)
    }

    /** 画质不同但复用同一音频规格时仍创建独立任务，源文件路径也相互隔离。 */
    @Test
    fun createDifferentQualityWithSharedAudio() = runBlocking {
        val first = createTask(resources = resources)
        val second = createTask(resources = listOf(video.copy(dashId = 64L), audio))

        assertEquals(2, queryTestTasks().size)
        val firstFiles =
            DownloadRepository.queryDashList(entity = first).map { it.getOutputFile() }.toSet()
        val secondFiles =
            DownloadRepository.queryDashList(entity = second).map { it.getOutputFile() }.toSet()
        assertTrue("不同任务的源文件路径必须隔离", firstFiles.intersect(secondFiles).isEmpty())
    }

    /** 视频规格相同但选择另一音频时允许同时建任务。 */
    @Test
    fun createDifferentAudioWithSharedVideo() = runBlocking {
        createTask(resources = resources)
        createTask(resources = listOf(video, audio.copy(dashId = 30216L)))

        assertEquals(2, queryTestTasks().size)
    }

    /** 单独音频、单独视频与音视频合成是三个不同组合，不能合并多个任务的资源来去重。 */
    @Test
    fun singleStreamsAndMergedCombinationAreIndependent() = runBlocking {
        createTask(resources = listOf(video))
        createTask(resources = listOf(audio))
        createTask(resources = resources)

        assertEquals(3, queryTestTasks().size)
    }

    /** 组合比较不受列表顺序、重复输入或可变的编码描述影响。 */
    @Test
    fun reorderedAndRepeatedStreamsStillMatchActiveCombination() = runBlocking {
        val first = createTask(resources = resources + audio)
        val duplicate = DownloadRepository.createNewRecordIfAbsent(
            bvid = testBvid,
            cid = PART_CID,
            resources = listOf(audio.copy(codecs = "updated metadata"), video),
        )

        assertNull(duplicate)
        assertEquals(1, queryTestTasks().size)
        assertEquals(2, DownloadRepository.queryDashList(entity = first).size)
    }

    /** 即使 DASH ID 和编码 ID 相同，不同资源类型也不属于同一组合。 */
    @Test
    fun resourceTypeParticipatesInCombinationIdentity() = runBlocking {
        createTask(resources = listOf(video))
        createTask(resources = listOf(video.copy(type = DashType.AUDIO)))

        assertEquals(2, queryTestTasks().size)
    }

    /** 排队、下载、合成和发布期间均只拦截相同组合，不以执行器的内存状态代替持久记录。 */
    @Test
    fun activeStagesBlockOnlyIdenticalCombination() = runBlocking {
        // 各阶段使用不同分集，独立验证持久状态对去重范围的约束。
        val statuses = listOf(
            TaskStatus.PREPARE,
            TaskStatus.DOWNLOADING,
            TaskStatus.SYNTHESIS,
            TaskStatus.PUBLISHING
        )
        for (status in statuses) {
            val cid = PART_CID + status.code
            val first = createTask(resources = resources, cid = cid)
            DownloadRepository.update(downloadTask = first.copy(status = status.code))

            assertNull(
                status.name,
                DownloadRepository.createNewRecordIfAbsent(
                    bvid = testBvid,
                    cid = cid,
                    resources = resources
                ),
            )
            createTask(resources = listOf(video.copy(codecId = 12L), audio), cid = cid)
        }
        // 每个阶段均保留原组合和另一个编码组合，重复请求未额外落库。
        assertEquals(8, queryTestTasks().size)
    }

    /** 已完成和失败记录不占用活动组合，后续新建请求保持既有语义。 */
    @Test
    fun completedAndFailedTasksAllowAnotherIdenticalCombination() = runBlocking {
        // 已完成和各类失败分别占用独立分集，避免另一轮新任务干扰状态判断。
        val statuses = listOf(
            TaskStatus.COMPLETED,
            TaskStatus.DOWNLOAD_FAILED,
            TaskStatus.SYNTHESIS_FAILED,
            TaskStatus.PUBLISH_FAILED
        )
        for (status in statuses) {
            val cid = PART_CID + status.code
            val first = createTask(resources = resources, cid = cid)
            DownloadRepository.update(downloadTask = first.copy(status = status.code))
            createTask(resources = resources, cid = cid)
        }
        assertEquals(8, queryTestTasks().size)
    }

    /** 分集和稿件身份不同的相同规格允许分别建任务。 */
    @Test
    fun identicalCombinationForDifferentVideoOrPartIsIndependent() = runBlocking {
        createTask(resources = resources)
        createTask(resources = resources, cid = PART_CID + 1)
        createTask(resources = resources, bvid = "$testBvid-other")

        assertEquals(3, queryTestTasks().size)
    }

    /** 同时提交的重复请求必须共享同一事务去重边界，只生成一套完整的任务及 DASH 记录。 */
    @Test
    fun concurrentIdenticalRequestsCreateExactlyOneTask() = runBlocking {
        // 同时开放全部请求，去重必须由 Repository 的真实事务保证。
        val gate = CompletableDeferred<Unit>()
        val requests = List(12) {
            async(Dispatchers.Default) {
                gate.await()
                DownloadRepository.createNewRecordIfAbsent(
                    bvid = testBvid,
                    cid = PART_CID,
                    resources = resources
                )
            }
        }
        gate.complete(Unit)
        val createdIds = requests.awaitAll().filterNotNull()

        // 唯一任务同时拥有稳定 groupId 和完整的 DASH 记录。
        assertEquals(1, createdIds.size)
        val task = queryTestTasks().single()
        assertEquals(createdIds.single(), task.id)
        assertEquals(task.id, task.groupId)
        assertEquals(2, DownloadRepository.queryDashList(entity = task).size)
    }

    /** 不同组合与其重复请求混合到达时，独立组合均保留且不能互相替换 DASH 记录。 */
    @Test
    fun concurrentDifferentCombinationsRemainIndependent() = runBlocking {
        // 混合三种有重叠资源的组合，每种组合都包含并发重复提交。
        val selections = listOf(resources, listOf(video.copy(codecId = 12L), audio), listOf(audio))
        val gate = CompletableDeferred<Unit>()
        val requests = List(12) { index ->
            async(Dispatchers.Default) {
                gate.await()
                DownloadRepository.createNewRecordIfAbsent(
                    bvid = testBvid,
                    cid = PART_CID,
                    resources = selections[index % selections.size],
                )
            }
        }
        gate.complete(Unit)
        val createdIds = requests.awaitAll().filterNotNull()

        // 每种组合只生成一套记录，提交顺序不影响持久化的资源归属。
        assertEquals(3, createdIds.size)
        val tasks = queryTestTasks()
        assertEquals(createdIds.toSet(), tasks.map { it.id }.toSet())
        val storedSelections = tasks.map { task ->
            DownloadRepository.queryDashList(entity = task).map { dash ->
                BiliDashModel(
                    type = dash.type,
                    dashId = dash.dashId,
                    codecId = dash.codecId,
                    mimeType = dash.mimeType,
                    codecs = dash.codecs
                )
            }.toSet()
        }.toSet()
        assertEquals(selections.map { it.toSet() }.toSet(), storedSelections)
    }

    /** 空选择不能产生没有 DASH 记录的任务。 */
    @Test
    fun emptySelectionDoesNotCreateTask() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                DownloadRepository.createNewRecordIfAbsent(
                    bvid = testBvid,
                    cid = PART_CID,
                    resources = emptyList()
                )
            }
        }
        runBlocking { assertEquals(0, queryTestTasks().size) }
    }

    /** 提交测试组合并返回真实持久记录，空结果直接报告为组合被错误拦截。 */
    private suspend fun createTask(
        resources: List<BiliDashModel>,
        cid: Long = PART_CID,
        bvid: String = testBvid,
    ): DownloadTaskEntity {
        val taskId = DownloadRepository.createNewRecordIfAbsent(
            bvid = bvid,
            cid = cid,
            resources = resources
        )
        assertNotNull("所选音视频组合应创建独立任务", taskId)
        return requireNotNull(
            database.downloadTaskDao().getDownloadTaskByTaskId(id = requireNotNull(taskId))
        )
    }

    private suspend fun queryTestTasks(): List<DownloadTaskEntity> = database.downloadTaskDao()
        .queryDownloadTask(statuses = TaskStatus.entries.map { it.code }.toIntArray())
        .filter { it.biliBvid.startsWith(testBvid) }

    private companion object {
        private const val PART_CID = 7600L
    }
}
