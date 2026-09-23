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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    /** 已完成处理的分 P 数量；跳过数包括解析失败、规格不匹配和已有活动任务。 */
    data class Progress(
        val processedCount: Int,
        val total: Int,
        val addedCount: Int,
        val skippedCount: Int,
    )

    /** 解析列表资源并确定下载范围，按分 P 身份去重后逐项入队。 */
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

    /**
     * 使用详情页已取得的分 P 快照建任务，无需再次解析稿件。
     *
     * 单项失败计入跳过数；协程取消会停止后续入队，已创建的任务继续由下载服务管理。
     * [onProgress] 在调用协程内回报已处理数量，首次规格选择取消时返回 [Result.Cancelled]。
     */
    suspend fun executeParts(
        parts: List<BiliVideoPartModel>,
        selectStreams: suspend (StreamSelectionRequest) -> BatchDownloadResolver.StreamSelection?,
        onProgress: (Progress) -> Unit = {},
    ): Result = enqueueParts(
        parts = parts.distinctBy { it.bvid to it.cid },
        resolveFailureCount = 0,
        selectStreams = selectStreams,
        onProgress = onProgress,
    )

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

    /** 获取首个可用规格后逐项处理，避免大批次预加载全部播放流及单项失败中断整批。 */
    private suspend fun enqueueParts(
        parts: List<BiliVideoPartModel>,
        resolveFailureCount: Int,
        selectStreams: suspend (
            StreamSelectionRequest
        ) -> BatchDownloadResolver.StreamSelection?,
        onProgress: (Progress) -> Unit = {},
    ): Result {
        var skippedCount = resolveFailureCount
        var addedCount = 0
        var preferred: BatchDownloadResolver.StreamSelection? = null
        onProgress(Progress(0, parts.size, addedCount, skippedCount))

        // 输入已去重且固定；任何迟到的页面刷新都不改变本批次目标。
        for ((index, part) in parts.withIndex()) {
            currentCoroutineContext().ensureActive()
            val dash = loadAvailableDash(part)
            if (dash == null) {
                skippedCount++
            } else {
                // 第一个可播放分 P 决定规格，后续仅在配置要求时再次询问。
                val streams = if (preferred == null) {
                    selectStreams(StreamSelectionRequest(null, dash))
                        ?.also { preferred = it } ?: return Result.Cancelled
                } else {
                    resolveStreams(part, dash, preferred, selectStreams)
                }
                currentCoroutineContext().ensureActive()
                val created = streams?.let { enqueueIfAvailable(part, it) } ?: false
                if (created) addedCount++ else skippedCount++
            }
            onProgress(Progress(index + 1, parts.size, addedCount, skippedCount))
        }
        return Result.Completed(addedCount, skippedCount, parts.size)
    }

    /** 将单项网络或解析错误转为跳过，取消始终交给调用方处理。 */
    private suspend fun loadAvailableDash(part: BiliVideoPartModel): BiliPlayStreamDash? = try {
        (loadDash(part) as? ResultWrapper.Success)?.value
            ?.takeIf { !it.video.isNullOrEmpty() || it.getAllAudio().isNotEmpty() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /** 只提交含有效资源的任务，保留底层去重结果与取消语义。 */
    private suspend fun enqueueIfAvailable(
        part: BiliVideoPartModel,
        streams: BatchDownloadResolver.StreamSelection,
    ): Boolean {
        val resources = buildDashModels(streams)
        if (resources.isEmpty()) return false
        return try {
            enqueueDownload(part, resources)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
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
                val call = NetworkManager.biliVideoRepository.requestPlayStreamDash(
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
                continuation.invokeOnCancellation { call.cancel() }
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
