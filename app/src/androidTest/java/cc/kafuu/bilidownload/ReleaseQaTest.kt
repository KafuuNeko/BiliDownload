package cc.kafuu.bilidownload

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.QaExportDirectory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.download.BatchExportUseCase
import cc.kafuu.bilidownload.common.download.ResourceExportUseCase
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.ExportFileNameUtils
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.feature.compose.activity.MediaPlayerActivity
import cc.kafuu.bilidownload.feature.compose.activity.MusicPlayerActivity
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiState
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerViewModel
import cc.kafuu.bilidownload.service.MusicPlaybackService
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 使用独立的临时目录验证真实文件导出及播放器重建，不写入正式任务数据库。 */
@RunWith(AndroidJUnit4::class)
class ReleaseQaTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var root: File
    private lateinit var destination: File

    /** 每项测试独占资源目录，避免把用户媒体或其他用例的成品作为夹具。 */
    @Before
    fun createFixtureDirectory() {
        root = File(context.getExternalFilesDir(null), "resources/qa-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        destination = File(root, "export").apply { mkdirs() }
    }

    /** 仅删除当前用例创建的临时目录，保留正式资源与数据库。 */
    @After
    fun removeOwnedFixtures() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    /** 原始路径不可用时，通过真实 ContentResolver 读取仍有效的资源 URI。 */
    @Test
    fun batchExport_readsContentUriWhenOriginalPathIsMissing() = runBlocking {
        val original = source(name = "original.mp4", content = "complete media")
        val uri =
            checkNotNull(FileUtils.resolveReadUri(context = context, file = File(original.file)))
        val published =
            original.copy(file = File(root, "missing.mp4").path, contentUri = uri.toString())
        val result = exporter { listOf(published) }.execute(
            treeUri = Uri.EMPTY,
            sources = listOf(target(id = 1))
        ) {}
        assertEquals(BatchExportUseCase.Result.Completed(successCount = 1, total = 1), result)
        assertEquals("complete media", destination.listFiles()!!.single().readText())
        assertEquals("complete media", File(original.file).readText())
    }

    /** 重复目标只处理一次，缺失和查询异常不阻止其他资源导出，进度使用去重后的总数。 */
    @Test
    fun batchExport_countsMissingAndFailedSourcesAndContinues() = runBlocking {
        val original = source(name = "source.mp4", content = "complete media")
        val progress = mutableListOf<BatchExportUseCase.Progress>()
        // 注入缺失与查询失败，让计数同时覆盖成功、跳过和异常三种结果。
        val result = exporter { id ->
            when (id) {
                2L -> emptyList()
                3L -> throw IOException("query unavailable")
                else -> listOf(original)
            }
        }.execute(
            treeUri = Uri.EMPTY,
            sources = listOf(
                target(id = 1),
                target(id = 1),
                target(id = 2),
                target(id = 3),
                target(id = 4)
            )
        ) { progress += it }
        assertEquals(
            BatchExportUseCase.Result.Completed(
                successCount = 2,
                total = 4,
                skippedCount = 1
            ), result
        )
        assertEquals(2, destination.listFiles()!!.size)
        assertEquals((0..4).map { BatchExportUseCase.Progress(current = it, total = 4) }, progress)
        assertTrue(destination.listFiles()!!.all { it.readText() == "complete media" })
    }

    /** 合成文件缺失时回退到有效源资源，并为重名导出保留已有目标内容。 */
    @Test
    fun batchExport_preservesExistingFileAndFallsBackFromMissingMixedResource() = runBlocking {
        val existing = File(destination, "Video - Part.mp4").apply { writeText("existing") }
        val missing =
            source(name = "missing.mp4", content = "temporary").also { File(it.file).delete() }
        val original = source(
            name = "source.mp4",
            content = "new media"
        ).copy(type = DownloadResourceType.VIDEO)
        val result = exporter { listOf(missing, original) }.execute(
            treeUri = Uri.EMPTY,
            sources = listOf(target(id = 1))
        ) {}
        assertEquals(BatchExportUseCase.Result.Completed(successCount = 1, total = 1), result)
        assertEquals("existing", existing.readText())
        assertEquals("new media", File(destination, "Video - Part(1).mp4").readText())
    }

    /** 无效目录应在读取源资源之前返回明确结果。 */
    @Test
    fun batchExport_invalidDestinationDoesNotReadSources() = runBlocking {
        val useCase = BatchExportUseCase(
            contextProvider = { context },
            queryResources = { error("Unexpected query") },
            templatesProvider = { ExportFileNameUtils.Templates() },
            directoryProvider = { _, _ -> null },
        )
        assertEquals(
            BatchExportUseCase.Result.InvalidDestination,
            useCase.execute(treeUri = Uri.EMPTY, sources = listOf(target(id = 1))) {})
    }

    /** 全部资源缺失时不创建空成品，也不将批次误报为成功导出。 */
    @Test
    fun batchExport_allMissingSourcesProducesNoOutput() = runBlocking {
        val result = exporter { emptyList() }.execute(
            treeUri = Uri.EMPTY,
            sources = listOf(target(id = 1), target(id = 2))
        ) {}
        assertEquals(BatchExportUseCase.Result.NoExportableResources, result)
        assertTrue(destination.listFiles()!!.isEmpty())
    }

    /** 标题中的受限字符必须在真实文件导出前替换，不能交给文档提供方决定。 */
    @Test
    fun batchExport_sanitizesIllegalTitleCharactersForIssue73() = runBlocking {
        val illegalChars = listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        val illegalTitle = DownloadFileNameUtils.TemplateContext(
            videoName = "标题${illegalChars.joinToString("")}后",
            partName = "P1",
        )
        val original = source(name = "source.mp4", content = "complete media")
        val result = exporter { listOf(original) }
            .execute(
                treeUri = Uri.EMPTY,
                sources = listOf(
                    BatchExportUseCase.Source(
                        taskId = 1,
                        fileNameContext = illegalTitle
                    )
                )
            ) {}
        assertEquals(BatchExportUseCase.Result.Completed(successCount = 1, total = 1), result)
        val exported = destination.listFiles()!!.single()
        illegalChars.forEach { illegal ->
            assertFalse(
                "$illegal should not remain in ${exported.name}",
                exported.name.contains(illegal)
            )
        }
        assertEquals("标题_后 - P1.mp4", exported.name)
        assertEquals("complete media", exported.readText())
    }

    /** SAF 边界再次清理调用方名称，防止不经过模板的导出入口重新写入非法字符。 */
    @Test
    fun resolveUniqueDocumentName_sanitizesNameBeforeDocumentCreation() {
        val directory = QaExportDirectory(directory = DocumentFile.fromFile(destination))
        val name = FileUtils.resolveUniqueDocumentName(
            parent = directory,
            desiredName = """A\/:*?"<>|B.mp4""",
        )
        assertEquals("A_B.mp4", name)
    }

    /** 合并后的移动导出同时保留 URI 回退和模板命名，并在副本核验后删除源资源。 */
    @Test
    fun batchExport_moveReadsContentUriAndDeletesOnlyExportedSource() = runBlocking {
        val original = source(name = "source.mp4", content = "complete media")
        val uri =
            checkNotNull(FileUtils.resolveReadUri(context = context, file = File(original.file)))
        val published =
            original.copy(file = File(root, "missing.mp4").path, contentUri = uri.toString())
        val cleanup = ResourceExportUseCase { resource ->
            assertEquals(published, resource)
            assertEquals("complete media", File(destination, "Video - Part.mp4").readText())
            File(original.file).delete()
        }
        val result =
            exporter(resourceExportUseCase = cleanup) { id -> if (id == 1L) listOf(published) else emptyList() }
                .execute(
                    treeUri = Uri.EMPTY,
                    sources = listOf(target(id = 1), target(id = 2)),
                    deleteSourceAfterExport = true
                ) {}
        assertEquals(
            BatchExportUseCase.Result.Completed(
                successCount = 1,
                total = 2,
                skippedCount = 1,
                deletedSourceCount = 1
            ), result
        )
        assertFalse(File(original.file).exists())
        assertEquals("complete media", destination.listFiles()!!.single().readText())
    }

    /** 删除失败不丢弃已核验的副本，并独立统计清理失败与导出失败。 */
    @Test
    fun batchExport_moveRetainsBothCopiesWhenCleanupFails() = runBlocking {
        val original = source(name = "source.mp4", content = "complete media")
        val result =
            exporter(resourceExportUseCase = ResourceExportUseCase { false }) { listOf(original) }
                .execute(
                    treeUri = Uri.EMPTY,
                    sources = listOf(target(id = 1)),
                    deleteSourceAfterExport = true
                ) {}
        assertEquals(
            BatchExportUseCase.Result.Completed(
                successCount = 1,
                total = 1,
                sourceDeleteFailureCount = 1
            ),
            result
        )
        assertEquals("complete media", File(original.file).readText())
        assertEquals("complete media", destination.listFiles()!!.single().readText())
    }

    /** 无法创建目标副本时保留源资源，不允许清理阶段运行。 */
    @Test
    fun batchExport_movePreservesSourceWhenDestinationDisappears() = runBlocking {
        val original = source(name = "source.mp4", content = "complete media")
        val result =
            exporter(resourceExportUseCase = ResourceExportUseCase { error("Source must be retained") }) {
                assertTrue(destination.delete())
                listOf(original)
            }.execute(
                treeUri = Uri.EMPTY,
                sources = listOf(target(id = 1)),
                deleteSourceAfterExport = true
            ) {}
        assertEquals(BatchExportUseCase.Result.Completed(successCount = 0, total = 1), result)
        assertEquals("complete media", File(original.file).readText())
    }

    /** 文件路径与 Content URI 均应在重建时复用播放器，完整保留暂停的播放会话。 */
    @Test
    fun videoPlayer_recreationPreservesPositionSpeedAndPause() {
        val video = createVideo(name = "qa-video.mp4")
        val contentUri =
            checkNotNull(FileUtils.resolveReadUri(context = context, file = video)).toString()
        for (uri in listOf(null, contentUri)) {
            ActivityScenario.launch<MediaPlayerActivity>(
                videoIntent(
                    video = video,
                    contentUri = uri
                )
            )
                .use { scenario ->
                    waitForVideoReady(scenario = scenario)
                    lateinit var originalPlayer: Player

                    // 等到真实资源就绪后设置用户状态，避免以尚未加载的临时状态验证恢复。
                    scenario.onActivity { activity ->
                        val model = ViewModelProvider(activity)[MediaPlayerViewModel::class.java]
                        originalPlayer =
                            (model.uiStateFlow.value as MediaPlayerUiState.Playing).player
                        originalPlayer.pause()
                        originalPlayer.seekTo(12_000L)
                        model.onSetPlaybackSpeed(intent = MediaPlayerUiIntent.SetPlaybackSpeed(speed = 1.5f))
                        assertEquals(12_000L, originalPlayer.currentPosition)
                    }

                    // 同一会话的重建不能替换播放器或触发重新播放。
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        val state = ViewModelProvider(activity)[MediaPlayerViewModel::class.java]
                            .uiStateFlow.value as MediaPlayerUiState.Playing
                        assertSame(originalPlayer, state.player)
                        assertEquals(
                            "Recreation changed playback position",
                            12_000L,
                            state.player.currentPosition
                        )
                        assertEquals(1.5f, state.player.playbackParameters.speed, 0.01f)
                        assertEquals(1.5f, state.selectedPlaybackSpeed, 0.01f)
                        assertFalse(
                            "Recreation unexpectedly resumed paused media",
                            state.player.playWhenReady
                        )
                    }
                }
        }
    }

    /** singleTask 收到新 Intent 时切换媒体，之后重建绑定最新参数并保留新会话进度。 */
    @Test
    fun videoPlayer_newIntentSwitchesMediaAndRecreationKeepsLatestMedia() {
        val firstVideo = createVideo(name = "qa-first.mp4")
        val nextVideo = createVideo(name = "qa-next.mp4")
        val nextIntent = videoIntent(video = nextVideo, title = "Next video")
        ActivityScenario.launch<MediaPlayerActivity>(videoIntent(video = firstVideo))
            .use { scenario ->
                waitForVideoReady(scenario = scenario)
                lateinit var originalActivity: MediaPlayerActivity
                lateinit var originalPlayer: Player

                // 从已暂停且非默认倍速的会话发起系统新 Intent，覆盖真正的 onNewIntent 分发。
                scenario.onActivity { activity ->
                    originalActivity = activity
                    val model = ViewModelProvider(activity)[MediaPlayerViewModel::class.java]
                    originalPlayer = (model.uiStateFlow.value as MediaPlayerUiState.Playing).player
                    originalPlayer.pause()
                    originalPlayer.seekTo(12_000L)
                    model.onSetPlaybackSpeed(intent = MediaPlayerUiIntent.SetPlaybackSpeed(speed = 1.5f))
                    activity.startActivity(nextIntent)
                }
                waitUntil {
                    var switched = false
                    scenario.onActivity { activity ->
                        val state =
                            ViewModelProvider(activity)[MediaPlayerViewModel::class.java].uiStateFlow.value
                        switched =
                            (state as? MediaPlayerUiState.Playing)?.filePath == nextVideo.path
                    }
                    switched
                }
                waitForVideoReady(scenario = scenario)

                // 新请求重用宿主和播放器，但重置进度、倍速与暂停；再设置新进度验证重建参数。
                scenario.onActivity { activity ->
                    val state = ViewModelProvider(activity)[MediaPlayerViewModel::class.java]
                        .uiStateFlow.value as MediaPlayerUiState.Playing
                    assertSame(originalActivity, activity)
                    assertSame(originalPlayer, state.player)
                    assertEquals("Next video", state.title)
                    assertEquals(
                        Uri.fromFile(nextVideo),
                        state.player.currentMediaItem?.localConfiguration?.uri
                    )
                    assertTrue(state.player.currentPosition < 5_000L)
                    assertEquals(1.0f, state.player.playbackParameters.speed, 0.01f)
                    assertTrue(state.player.playWhenReady)
                    state.player.pause()
                    state.player.seekTo(7_000L)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val state = ViewModelProvider(activity)[MediaPlayerViewModel::class.java]
                        .uiStateFlow.value as MediaPlayerUiState.Playing
                    assertSame(originalPlayer, state.player)
                    assertEquals(nextVideo.path, state.filePath)
                    assertEquals(7_000L, state.player.currentPosition)
                    assertFalse(state.player.playWhenReady)
                }
            }
    }

    /** 资源暂时缺失时重建保留错误，新 Intent 可重新打开同一 URI 并恢复播放。 */
    @Test
    fun videoPlayer_newIntentCanRetrySameUriAfterError() {
        val missingVideo = File(root, "qa-restored.mp4")
        val intent = videoIntent(video = missingVideo)
        ActivityScenario.launch<MediaPlayerActivity>(intent).use { scenario ->
            waitUntil {
                var failed = false
                scenario.onActivity {
                    failed = ViewModelProvider(it)[MediaPlayerViewModel::class.java]
                        .uiStateFlow.value is MediaPlayerUiState.Error
                }
                failed
            }

            // 重建不应暗中重试失败资源，避免把错误页再次变成加载或播放状态。
            scenario.recreate()
            scenario.onActivity {
                assertTrue(
                    ViewModelProvider(it)[MediaPlayerViewModel::class.java]
                        .uiStateFlow.value is MediaPlayerUiState.Error
                )
            }

            // 同一路径补回有效文件后，显式新请求必须绕过会话复用并重新准备资源。
            createVideo(name = missingVideo.name)
            // ActivityScenario 会给启动 Intent 添加 CLEAR_TASK，不能复用它模拟普通新请求。
            scenario.onActivity { it.startActivity(videoIntent(video = missingVideo)) }
            waitForVideoReady(scenario = scenario)
            scenario.onActivity {
                val state = ViewModelProvider(it)[MediaPlayerViewModel::class.java]
                    .uiStateFlow.value as MediaPlayerUiState.Playing
                assertEquals(
                    Uri.fromFile(missingVideo),
                    state.player.currentMediaItem?.localConfiguration?.uri
                )
                assertTrue(state.player.playWhenReady)
            }
        }
    }

    /** 暂停的视频返回时关闭宿主，避免会话复用守卫影响正常退出或意外进入画中画。 */
    @Test
    fun videoPlayer_pausedBackClosesActivity() {
        val video = createVideo(name = "qa-close.mp4")
        ActivityScenario.launch<MediaPlayerActivity>(videoIntent(video = video)).use { scenario ->
            waitForVideoReady(scenario = scenario)
            scenario.onActivity {
                val state = ViewModelProvider(it)[MediaPlayerViewModel::class.java]
                    .uiStateFlow.value as MediaPlayerUiState.Playing
                state.player.pause()
                it.onBackPressedDispatcher.onBackPressed()
            }
            waitUntil { scenario.state == Lifecycle.State.DESTROYED }
        }
    }

    /** 正在播放的视频返回时进入画中画，页面离开前台不会暂停播放器。 */
    @Test
    fun videoPlayer_backEntersPictureInPictureAndKeepsPlaying() {
        val video = File(root, "qa-pip.mp4")
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-f", "lavfi", "-i", "color=c=blue:s=160x90:r=10",
                "-t", "30", "-c:v", "mpeg4", video.path,
            )
        )
        assertTrue(ReturnCode.isSuccess(session.returnCode))
        val intent = MediaPlayerActivity.buildIntent(
            filePath = video.path,
            title = "QA PiP",
            mimeType = "video/mp4"
        ).apply {
            setClass(context, MediaPlayerActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 等待实际播放后再返回，防止把未开始播放时的正常关闭误判为画中画失败。
        ActivityScenario.launch<MediaPlayerActivity>(intent).use { scenario ->
            waitUntil {
                var playing = false
                scenario.onActivity {
                    val state =
                        ViewModelProvider(it)[MediaPlayerViewModel::class.java].uiStateFlow.value
                    playing = (state as? MediaPlayerUiState.Playing)?.player?.isPlaying == true
                }
                playing
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitUntil {
                var inPip = false
                scenario.onActivity { inPip = it.isInPictureInPictureMode }
                inPip
            }
            scenario.onActivity {
                val state =
                    ViewModelProvider(it)[MediaPlayerViewModel::class.java].uiStateFlow.value
                        as MediaPlayerUiState.Playing
                assertTrue(state.player.isPlaying)
                it.finish()
            }
        }
    }

    /** 页面进入后台后由音乐服务继续持有播放器，并推进真实媒体时间。 */
    @Test
    fun musicPlayer_keepsPlayingAfterActivityMovesToBackground() {
        val audio = File(root, "qa-audio.m4a")
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-f", "lavfi", "-i", "anullsrc=r=16000:cl=mono",
                "-t", "30", "-c:a", "aac", audio.path,
            )
        )
        assertTrue(ReturnCode.isSuccess(session.returnCode))
        val intent = MusicPlayerActivity.buildIntent(
            filePath = audio.path,
            title = "QA audio",
            mimeType = "audio/mp4"
        ).apply {
            setClass(context, MusicPlayerActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 控制器独立连接服务，页面停止后仍能验证后台会话的播放事实。
        ActivityScenario.launch<MusicPlayerActivity>(intent).use { scenario ->
            val future = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
            )
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync()
            try {
                val controller = future.get(10, TimeUnit.SECONDS)
                waitUntil {
                    var playing = false
                    instrumentation.runOnMainSync { playing = controller.isPlaying }
                    playing
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                var initialPosition = 0L
                instrumentation.runOnMainSync { initialPosition = controller.currentPosition }
                waitUntil {
                    var progressed = false
                    instrumentation.runOnMainSync {
                        progressed =
                            controller.isPlaying && controller.currentPosition > initialPosition + 300L
                    }
                    progressed
                }
                instrumentation.runOnMainSync { controller.stop() }
            } finally {
                // Media3 的读取、控制和释放必须统一运行在控制器的 application looper。
                instrumentation.runOnMainSync { MediaController.releaseFuture(future) }
                context.stopService(Intent(context, MusicPlaybackService::class.java))
            }
        }
    }

    /** 等待真实播放器状态，超时后明确失败，避免靠固定长延迟掩盖错误。 */
    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Player did not reach the expected state within 10 seconds")
    }

    /** 生成仅用于当前临时目录的短视频，提供不依赖账号或网络的真实解码输入。 */
    private fun createVideo(name: String): File {
        val video = File(root, name)
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-f", "lavfi", "-i", "color=c=blue:s=160x90:r=10",
                "-t", "30", "-c:v", "mpeg4", video.path,
            )
        )
        assertTrue(
            "Unable to create synthetic QA media: ${session.returnCode}",
            ReturnCode.isSuccess(session.returnCode)
        )
        return video
    }

    /** 构造真实 Activity 启动参数，复用应用已有的 singleTask 路由。 */
    private fun videoIntent(video: File, title: String = "QA video", contentUri: String? = null) =
        MediaPlayerActivity.buildIntent(
            filePath = video.path,
            title = title,
            mimeType = "video/mp4",
            contentUri = contentUri
        ).apply {
            setClass(context, MediaPlayerActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 在主线程检查播放器就绪状态，确保后续断言基于真实媒体加载结果。 */
    private fun waitForVideoReady(scenario: ActivityScenario<MediaPlayerActivity>) {
        waitUntil {
            var ready = false
            scenario.onActivity {
                val state =
                    ViewModelProvider(it)[MediaPlayerViewModel::class.java].uiStateFlow.value
                ready =
                    (state as? MediaPlayerUiState.Playing)?.player?.playbackState == Player.STATE_READY
            }
            ready
        }
    }

    private fun exporter(
        resourceExportUseCase: ResourceExportUseCase = ResourceExportUseCase { error("Source must be retained") },
        query: suspend (Long) -> List<DownloadResourceEntity>,
    ) = BatchExportUseCase(
        contextProvider = { context }, queryResources = query,
        templatesProvider = { ExportFileNameUtils.Templates() },
        directoryProvider = { _, _ ->
            QaExportDirectory(
                directory = DocumentFile.fromFile(
                    destination
                )
            )
        },
        resourceExportUseCase = resourceExportUseCase,
    )

    private fun target(id: Long) = BatchExportUseCase.Source(
        taskId = id,
        fileNameContext = DownloadFileNameUtils.TemplateContext(
            videoName = "Video",
            partName = "Part"
        ),
    )

    private fun source(name: String, content: String): DownloadResourceEntity {
        val file = File(root, name).apply { writeText(content) }
        return DownloadResourceEntity(
            taskId = 1, type = DownloadResourceType.MIXED, name = name, mimeType = "video/mp4",
            storageSizeBytes = file.length(), creationTime = 0, file = file.path,
        )
    }

}
