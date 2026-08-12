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
import org.junit.Assert.assertEquals
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
