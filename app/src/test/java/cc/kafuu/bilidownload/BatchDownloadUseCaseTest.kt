package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.download.BatchDownloadResolver
import cc.kafuu.bilidownload.common.download.BatchDownloadUseCase
import cc.kafuu.bilidownload.common.model.BatchQualityMismatchMode
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.network.model.BiliPlayDolby
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamSegmentBase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDownloadUseCaseTest {
    @Test
    fun execute_returnsNoCandidatesWithoutRequestingUserInput() = runBlocking {
        val useCase = BatchDownloadUseCase(
            resolveSources = {
                BatchDownloadResolver.ResolveResult(emptyList(), emptyList())
            },
        )

        val result = useCase.execute(
            sources = listOf(source()),
            selectScope = { error("不应请求下载范围") },
            selectStreams = { error("不应请求下载流") },
        )

        assertEquals(BatchDownloadUseCase.Result.NoCandidates, result)
    }

    @Test
    fun execute_downloadsAllPartsUsingTheInitialSelection() = runBlocking {
        val firstPart = part(cid = 1)
        val secondPart = part(cid = 2)
        val selectedVideo = resource(id = 80, codecId = 7, suffix = "selected")
        val enqueuedParts = mutableListOf<Long>()
        var scopeRequest: BatchDownloadUseCase.ScopeSelectionRequest? = null
        var streamSelectionCount = 0
        val useCase = BatchDownloadUseCase(
            resolveSources = {
                resolvedResult(parts = listOf(firstPart, secondPart))
            },
            loadDash = { currentPart ->
                ResultWrapper.Success(
                    dash(
                        listOf(
                            resource(
                                id = 80,
                                codecId = 7,
                                suffix = "part-${currentPart.cid}",
                            )
                        )
                    )
                )
            },
            mismatchModeProvider = { BatchQualityMismatchMode.AUTO_FALLBACK },
            enqueueDownload = { currentPart, _ ->
                enqueuedParts += currentPart.cid
                true
            },
        )

        val result = useCase.execute(
            sources = listOf(source()),
            selectScope = {
                scopeRequest = it
                BatchDownloadUseCase.DownloadScope.ALL_PARTS
            },
            selectStreams = {
                streamSelectionCount++
                BatchDownloadResolver.StreamSelection(selectedVideo, null)
            },
        )

        assertEquals(
            BatchDownloadUseCase.ScopeSelectionRequest(1, 2, 0),
            scopeRequest,
        )
        assertEquals(1, streamSelectionCount)
        assertEquals(listOf(1L, 2L), enqueuedParts)
        assertEquals(BatchDownloadUseCase.Result.Completed(2, 0, 2), result)
    }

    @Test
    fun execute_countsUnplayablePartsAndUsesFirstPlayablePartForSelection() = runBlocking {
        val firstPart = part(cid = 1)
        val secondPart = part(cid = 2)
        val thirdPart = part(cid = 3)
        val selectionRequests = mutableListOf<BatchDownloadUseCase.StreamSelectionRequest>()
        val useCase = BatchDownloadUseCase(
            resolveSources = {
                resolvedResult(parts = listOf(firstPart, secondPart, thirdPart))
            },
            loadDash = { currentPart ->
                if (currentPart == secondPart) {
                    ResultWrapper.Success(dash(listOf(resource(80, 7, "playable"))))
                } else {
                    ResultWrapper.Error("不可播放")
                }
            },
            enqueueDownload = { _, _ -> true },
        )

        val result = useCase.execute(
            sources = listOf(source()),
            selectScope = { BatchDownloadUseCase.DownloadScope.ALL_PARTS },
            selectStreams = {
                selectionRequests += it
                BatchDownloadResolver.StreamSelection(it.dash.video?.first(), null)
            },
        )

        assertEquals(1, selectionRequests.size)
        assertEquals(null, selectionRequests.single().partTitle)
        assertEquals(BatchDownloadUseCase.Result.Completed(1, 2, 3), result)
    }

    @Test
    fun execute_requestsAnotherSelectionForAskMismatchMode() = runBlocking {
        val firstPart = part(cid = 1)
        val secondPart = part(cid = 2)
        val selectionTitles = mutableListOf<String?>()
        val useCase = BatchDownloadUseCase(
            resolveSources = {
                resolvedResult(parts = listOf(firstPart, secondPart))
            },
            loadDash = { currentPart ->
                val quality = if (currentPart == firstPart) 80L else 64L
                ResultWrapper.Success(
                    dash(listOf(resource(quality, 7, "part-${currentPart.cid}")))
                )
            },
            mismatchModeProvider = { BatchQualityMismatchMode.ASK },
            enqueueDownload = { _, _ -> true },
        )

        val result = useCase.execute(
            sources = listOf(source()),
            selectScope = { BatchDownloadUseCase.DownloadScope.ALL_PARTS },
            selectStreams = { request ->
                selectionTitles += request.partTitle
                BatchDownloadResolver.StreamSelection(request.dash.video?.first(), null)
            },
        )

        assertEquals(listOf(null, secondPart.name), selectionTitles)
        assertEquals(BatchDownloadUseCase.Result.Completed(2, 0, 2), result)
    }

    @Test
    fun execute_stopsWhenInitialStreamSelectionIsCancelled() = runBlocking {
        var enqueueCount = 0
        val useCase = BatchDownloadUseCase(
            resolveSources = { resolvedResult(parts = listOf(part(cid = 1))) },
            loadDash = {
                ResultWrapper.Success(dash(listOf(resource(80, 7, "playable"))))
            },
            enqueueDownload = { _, _ ->
                enqueueCount++
                true
            },
        )

        val result = useCase.execute(
            sources = listOf(source()),
            selectScope = { error("单分 P 资源不应请求下载范围") },
            selectStreams = { null },
        )

        assertEquals(BatchDownloadUseCase.Result.Cancelled, result)
        assertEquals(0, enqueueCount)
    }

    /** 全部分 P 使用固定身份去重，并逐项回报新增与跳过，不因单项异常中断。 */
    @Test
    fun executeParts_deduplicatesAndReportsPartialFailures() = runBlocking {
        val progress = mutableListOf<BatchDownloadUseCase.Progress>()
        val submitted = mutableListOf<Long>()
        val useCase = BatchDownloadUseCase(
            resolveSources = { error("详情快照不应再次解析稿件") },
            loadDash = {
                if (it.cid == 2L) throw IllegalStateException("不可播放")
                ResultWrapper.Success(dash(listOf(resource(80, 7, "part-${it.cid}"))))
            },
            mismatchModeProvider = { BatchQualityMismatchMode.SKIP },
            enqueueDownload = { current, _ ->
                submitted += current.cid
                if (current.cid == 3L) throw IllegalStateException("入队失败")
                current.cid != 4L
            },
        )
        val result = useCase.executeParts(
            listOf(part(1), part(1), part(2), part(3), part(4), part(5)),
            selectStreams = { BatchDownloadResolver.StreamSelection(it.dash.video?.first(), null) },
            onProgress = { progress += it },
        )
        assertEquals(BatchDownloadUseCase.Result.Completed(2, 3, 5), result)
        assertEquals(listOf(1L, 3L, 4L, 5L), submitted)
        assertEquals((0..5).toList(), progress.map { it.processedCount })
        assertEquals(BatchDownloadUseCase.Progress(5, 5, 2, 3), progress.last())
    }

    /** 大批次仅用首个可播放分 P 选择规格，后续按顺序执行且不丢项。 */
    @Test
    fun executeParts_handles170PartsWithOneStreamSelection() = runBlocking {
        val parts = (1L..170L).map(::part)
        val submitted = mutableListOf<Long>()
        var selections = 0
        val useCase = BatchDownloadUseCase(
            loadDash = { ResultWrapper.Success(dash(listOf(resource(80, 7, "part-${it.cid}")))) },
            enqueueDownload = { current, _ -> submitted += current.cid; true },
        )
        val result = useCase.executeParts(parts, selectStreams = {
            selections++
            BatchDownloadResolver.StreamSelection(it.dash.video?.first(), null)
        })
        assertEquals(BatchDownloadUseCase.Result.Completed(170, 0, 170), result)
        assertEquals(parts.map { it.cid }, submitted)
        assertEquals(1, selections)
    }

    /** 中途取消不吞成普通跳过，已经提交的任务保持原身份且后续项不再处理。 */
    @Test
    fun executeParts_propagatesCancellationAndStopsLaterSubmissions() = runBlocking {
        val submitted = mutableListOf<Long>()
        val useCase = BatchDownloadUseCase(
            loadDash = {
                if (it.cid == 2L) throw CancellationException("cancelled")
                ResultWrapper.Success(dash(listOf(resource(80, 7, "playable"))))
            },
            enqueueDownload = { current, _ -> submitted += current.cid; true },
        )
        var cancelled = false
        try {
            useCase.executeParts(listOf(part(1), part(2), part(3)), selectStreams = {
                BatchDownloadResolver.StreamSelection(it.dash.video?.first(), null)
            })
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(listOf(1L), submitted)
    }

    /** 空播放流应跳过，避免空资源列表被视为用户确认的有效下载任务。 */
    @Test
    fun executeParts_skipsEmptyStreamsWithoutPrompting() = runBlocking {
        val useCase = BatchDownloadUseCase(
            loadDash = { ResultWrapper.Success(dash(emptyList())) },
            enqueueDownload = { _, _ -> error("不应创建空资源任务") },
        )
        val result = useCase.executeParts(listOf(part(1)), selectStreams = { error("不应显示空规格") })
        assertEquals(BatchDownloadUseCase.Result.Completed(0, 1, 1), result)
    }

    private fun resolvedResult(parts: List<BiliVideoPartModel>) =
        BatchDownloadResolver.ResolveResult(
            candidates = listOf(
                BatchDownloadResolver.ResolvedCandidate(
                    source = source(),
                    parts = parts,
                    preferredPart = parts.first(),
                )
            ),
            failures = emptyList(),
        )

    private fun source() = BiliVideoModel(
        title = "title",
        cover = "cover",
        description = "description",
        pubDate = 0,
        author = "author",
        bvid = "BV1test",
        duration = "00:01",
    )

    private fun part(cid: Long) = BiliVideoPartModel(
        bvid = "BV1test",
        cid = cid,
        name = "part-$cid",
        remark = null,
    )

    private fun resource(id: Long, codecId: Long, suffix: String) =
        BiliPlayStreamResource(
            id = id,
            baseUrl = "https://example.com/$suffix",
            backupUrl = emptyList(),
            bandwidth = 0,
            mimeType = "video/mp4",
            codecs = "test",
            width = 0,
            height = 0,
            frameRate = "0",
            sar = "1:1",
            startWithSap = 0,
            segmentBase = BiliPlayStreamSegmentBase("", ""),
            codecId = codecId,
        )

    private fun dash(video: List<BiliPlayStreamResource>) = BiliPlayStreamDash(
        duration = 0,
        minBufferTime = 0.0,
        video = video,
        audio = null,
        supportFormats = emptyList(),
        dolby = BiliPlayDolby(type = 0, audio = null),
        flac = null,
    )
}
