package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.BatchQualityMismatchMode
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.model.bili.BiliResourceModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 批量下载资源列表条目的应用用例。
 *
 * 该类负责资源解析、播放流加载、画质不匹配策略以及下载任务入队；需要用户作出
 * 选择的步骤通过回调交给调用方，因此不依赖具体页面或对话框实现。
 */
class BatchDownloadUseCase(
    private val resolveSources: suspend (
        List<BiliResourceModel>
    ) -> BatchDownloadResolver.ResolveResult = BatchDownloadResolver::resolve,
    private val loadDash: suspend (
        BiliVideoPartModel
    ) -> ResultWrapper<BiliPlayStreamDash, String> = ::loadPartDash,
    private val mismatchModeProvider: () -> BatchQualityMismatchMode = {
        AppModel.batchQualityMismatchMode
    },
    private val enqueueDownload: suspend (
        BiliVideoPartModel,
        List<BiliDashModel>
    ) -> Boolean = ::enqueuePart,
) {
    enum class DownloadScope {
        PREFERRED_PART,
        ALL_PARTS,
    }

    data class ScopeSelectionRequest(
        val sourceCount: Int,
        val totalPartCount: Int,
        val resolveFailureCount: Int,
    )

    data class StreamSelectionRequest(
        /** 首次选择时为空，由 UI 使用通用标题。 */
        val partTitle: String?,
        val dash: BiliPlayStreamDash,
    )

    sealed interface Result {
        data object NoCandidates : Result
        data object Cancelled : Result

        data class Completed(
            val addedCount: Int,
            val skippedCount: Int,
            val requestedPartCount: Int,
        ) : Result
    }

    suspend fun execute(
        sources: List<BiliResourceModel>,
        selectScope: suspend (ScopeSelectionRequest) -> DownloadScope?,
        selectStreams: suspend (
            StreamSelectionRequest
        ) -> BatchDownloadResolver.StreamSelection?,
    ): Result {
        val resolveResult = resolveSources(sources)
        if (resolveResult.candidates.isEmpty()) return Result.NoCandidates

        val scope = selectDownloadScope(resolveResult, selectScope) ?: return Result.Cancelled
        val parts = resolveResult.candidates.flatMap { candidate ->
            when (scope) {
                DownloadScope.PREFERRED_PART -> listOf(candidate.preferredPart)
                DownloadScope.ALL_PARTS -> candidate.parts
            }
        }.distinctBy { it.bvid to it.cid }

        return enqueueParts(
            parts = parts,
            resolveFailureCount = resolveResult.failures.size,
            selectStreams = selectStreams,
        )
    }

    private suspend fun selectDownloadScope(
        result: BatchDownloadResolver.ResolveResult,
        selectScope: suspend (ScopeSelectionRequest) -> DownloadScope?,
    ): DownloadScope? {
        if (result.candidates.none { it.parts.size > 1 }) {
            return DownloadScope.PREFERRED_PART
        }

        return selectScope(
            ScopeSelectionRequest(
                sourceCount = result.candidates.size,
                totalPartCount = result.candidates.sumOf { it.parts.size },
                resolveFailureCount = result.failures.size,
            )
        )
    }

    private suspend fun enqueueParts(
        parts: List<BiliVideoPartModel>,
        resolveFailureCount: Int,
        selectStreams: suspend (
            StreamSelectionRequest
        ) -> BatchDownloadResolver.StreamSelection?,
    ): Result {
        if (parts.isEmpty()) {
            return Result.Completed(
                addedCount = 0,
                skippedCount = resolveFailureCount,
                requestedPartCount = 0,
            )
        }

        var skippedCount = resolveFailureCount
        var firstPlayableIndex = -1
        var firstDash: BiliPlayStreamDash? = null
        for ((index, part) in parts.withIndex()) {
            when (val result = loadDash(part)) {
                is ResultWrapper.Success -> {
                    firstPlayableIndex = index
                    firstDash = result.value
                    break
                }

                is ResultWrapper.Error -> skippedCount++
            }
        }

        val selectionDash = firstDash
            ?: return Result.Completed(
                addedCount = 0,
                skippedCount = skippedCount,
                requestedPartCount = parts.size,
            )
        val preferredStreams = selectStreams(
            StreamSelectionRequest(partTitle = null, dash = selectionDash)
        ) ?: return Result.Cancelled

        var addedCount = 0
        for (index in firstPlayableIndex until parts.size) {
            val part = parts[index]
            val dash = if (index == firstPlayableIndex) {
                selectionDash
            } else {
                when (val result = loadDash(part)) {
                    is ResultWrapper.Success -> result.value
                    is ResultWrapper.Error -> {
                        skippedCount++
                        continue
                    }
                }
            }

            val streams = resolveStreams(part, dash, preferredStreams, selectStreams)
            if (streams == null) {
                skippedCount++
                continue
            }
            val resources = buildDashModels(streams)
            if (resources.isEmpty()) {
                skippedCount++
                continue
            }

            val taskCreated = runCatching {
                enqueueDownload(part, resources)
            }.getOrDefault(false)
            if (taskCreated) addedCount++ else skippedCount++
        }

        return Result.Completed(addedCount, skippedCount, parts.size)
    }

    private suspend fun resolveStreams(
        part: BiliVideoPartModel,
        dash: BiliPlayStreamDash,
        preferred: BatchDownloadResolver.StreamSelection,
        selectStreams: suspend (
            StreamSelectionRequest
        ) -> BatchDownloadResolver.StreamSelection?,
    ): BatchDownloadResolver.StreamSelection? {
        BatchDownloadResolver.selectExactStreams(
            preferred.videoStream,
            preferred.audioStream,
            dash,
        )?.let { return it }

        return when (mismatchModeProvider()) {
            BatchQualityMismatchMode.AUTO_FALLBACK ->
                BatchDownloadResolver.selectCompatibleStreams(
                    preferred.videoStream,
                    preferred.audioStream,
                    dash,
                )

            BatchQualityMismatchMode.ASK -> selectStreams(
                StreamSelectionRequest(partTitle = part.name, dash = dash)
            )

            BatchQualityMismatchMode.SKIP -> null
        }
    }

    private fun buildDashModels(
        streams: BatchDownloadResolver.StreamSelection
    ) = buildList {
        streams.videoStream?.let {
            add(BiliDashModel.create(DashType.VIDEO, it))
        }
        streams.audioStream?.let {
            add(BiliDashModel.create(DashType.AUDIO, it))
        }
    }

    companion object {
        private suspend fun loadPartDash(
            part: BiliVideoPartModel
        ): ResultWrapper<BiliPlayStreamDash, String> =
            suspendCancellableCoroutine { continuation ->
                NetworkManager.biliVideoRepository.requestPlayStreamDash(
                    part.bvid,
                    part.cid,
                    object : IServerCallback<BiliPlayStreamDash> {
                        override fun onSuccess(
                            httpCode: Int,
                            code: Int,
                            message: String,
                            data: BiliPlayStreamDash,
                        ) {
                            if (continuation.isActive) {
                                continuation.resume(ResultWrapper.Success(data))
                            }
                        }

                        override fun onFailure(httpCode: Int, code: Int, message: String) {
                            if (continuation.isActive) {
                                continuation.resume(ResultWrapper.Error(message))
                            }
                        }
                    }
                )
            }

        private suspend fun enqueuePart(
            part: BiliVideoPartModel,
            resources: List<BiliDashModel>,
        ): Boolean = DownloadManager.startDownload(
            CommonLibs.requireContext(),
            part.bvid,
            part.cid,
            resources,
        )
    }
}
