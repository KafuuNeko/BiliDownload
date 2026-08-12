package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliResourceModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 将不同列表页面中的稿件或剧集条目解析为实际可下载的分 P。
 *
 * 解析过程会请求稿件或季度详情。为了避免多选大量条目时瞬间发出过多请求，
 * 所有调用共享一个有界信号量。
 */
object BatchDownloadResolver {
    private const val MAX_RESOLVE_CONCURRENCY = 4
    private val mResolveSemaphore = Semaphore(MAX_RESOLVE_CONCURRENCY)

    data class ResolvedCandidate(
        val source: BiliResourceModel,
        val parts: List<BiliVideoPartModel>,
        val preferredPart: BiliVideoPartModel,
    )

    data class ResolveFailure(
        val source: BiliResourceModel,
        val message: String,
    )

    data class ResolveResult(
        val candidates: List<ResolvedCandidate>,
        val failures: List<ResolveFailure>,
    )

    data class StreamSelection(
        val videoStream: BiliPlayStreamResource?,
        val audioStream: BiliPlayStreamResource?,
    )

    private sealed interface CandidateResult {
        data class Success(val candidate: ResolvedCandidate) : CandidateResult
        data class Failure(val failure: ResolveFailure) : CandidateResult
    }

    suspend fun resolve(sources: List<BiliResourceModel>): ResolveResult = coroutineScope {
        val results = sources.map { source ->
            async(Dispatchers.IO) {
                mResolveSemaphore.withPermit {
                    runCatching { resolveCandidate(source) }.fold(
                        onSuccess = { CandidateResult.Success(it) },
                        onFailure = {
                            CandidateResult.Failure(
                                ResolveFailure(
                                    source = source,
                                    message = it.message.orEmpty().ifBlank { "未知错误" }
                                )
                            )
                        }
                    )
                }
            }
        }.awaitAll()

        ResolveResult(
            candidates = results.mapNotNull { (it as? CandidateResult.Success)?.candidate },
            failures = results.mapNotNull { (it as? CandidateResult.Failure)?.failure },
        )
    }

    private fun resolveCandidate(source: BiliResourceModel): ResolvedCandidate {
        return when (source) {
            is BiliVideoModel -> resolveVideo(source)
            is BiliMediaModel -> resolveMedia(source)
            else -> throw IllegalArgumentException("不支持的资源类型")
        }
    }

    private fun resolveVideo(source: BiliVideoModel): ResolvedCandidate {
        var failureMessage = ""
        val details = NetworkManager.biliVideoRepository.syncRequestVideoDetail(source.bvid) {
                _, _, message ->
            failureMessage = message
        } ?: throw IllegalStateException(failureMessage.ifBlank { "无法获取稿件详情" })

        val parts = details.pages.map {
            BiliVideoPartModel(
                bvid = details.bvid,
                cid = it.cid,
                name = it.part,
                remark = TimeUtils.formatSecondTime(it.duration)
            )
        }
        val preferredPart = parts.find { it.cid == source.preferredCid }
            ?: parts.firstOrNull()
            ?: throw IllegalStateException("稿件中没有可下载的分 P")
        return ResolvedCandidate(source, parts, preferredPart)
    }

    private fun resolveMedia(source: BiliMediaModel): ResolvedCandidate {
        var failureMessage = ""
        val details = if (source.seasonId != 0L) {
            NetworkManager.biliVideoRepository.syncRequestSeasonDetailBySeasonId(source.seasonId) {
                    _, _, message ->
                failureMessage = message
            }
        } else {
            NetworkManager.biliVideoRepository.syncRequestSeasonDetailByEpId(source.mediaId) {
                    _, _, message ->
                failureMessage = message
            }
        } ?: throw IllegalStateException(failureMessage.ifBlank { "无法获取剧集详情" })

        val parts = details.episodes.map {
            BiliVideoPartModel(
                bvid = it.bvid,
                cid = it.cid,
                name = "${it.title} ${it.longTitle}".trim(),
                remark = it.badge
            )
        }
        val preferredEpisodeId = source.preferredEpisodeId
        val preferredPart = details.episodes
            .indexOfFirst { it.id == preferredEpisodeId }
            .takeIf { it >= 0 }
            ?.let(parts::get)
            ?: parts.firstOrNull()
            ?: throw IllegalStateException("剧集中没有可下载的单集")
        return ResolvedCandidate(source, parts, preferredPart)
    }

    /**
     * 根据用户第一次选择的资源规格，为其他分 P 选择兼容资源。
     *
     * 优先级依次为：完全相同、相同编码的最近低档、相同档位、
     * 任意编码的最近低档。不会选择高于用户首次选择档位的资源。
     */
    fun selectCompatibleStream(
        preferred: BiliPlayStreamResource?,
        available: List<BiliPlayStreamResource>?
    ): BiliPlayStreamResource? {
        if (preferred == null) return null
        val streams = available.orEmpty()
        if (streams.isEmpty()) return null

        selectExactStream(preferred, streams)?.let { return it }

        streams.filter { it.codecId == preferred.codecId && it.id <= preferred.id }
            .maxByOrNull { it.id }
            ?.let { return it }

        streams.find { it.id == preferred.id }?.let { return it }

        streams.filter { it.id <= preferred.id }
            .maxByOrNull { it.id }
            ?.let { return it }

        return null
    }

    /** 根据档位和编码查找与首次选择完全一致的资源。 */
    fun selectExactStream(
        preferred: BiliPlayStreamResource?,
        available: List<BiliPlayStreamResource>?
    ): BiliPlayStreamResource? {
        if (preferred == null) return null
        return available.orEmpty().find {
            it.id == preferred.id && it.codecId == preferred.codecId
        }
    }

    fun selectExactStreams(
        preferredVideo: BiliPlayStreamResource?,
        preferredAudio: BiliPlayStreamResource?,
        dash: BiliPlayStreamDash
    ): StreamSelection? = selectStreams(
        preferredVideo = preferredVideo,
        preferredAudio = preferredAudio,
        dash = dash,
        selector = ::selectExactStream
    )

    fun selectCompatibleStreams(
        preferredVideo: BiliPlayStreamResource?,
        preferredAudio: BiliPlayStreamResource?,
        dash: BiliPlayStreamDash
    ): StreamSelection? = selectStreams(
        preferredVideo = preferredVideo,
        preferredAudio = preferredAudio,
        dash = dash,
        selector = ::selectCompatibleStream
    )

    private fun selectStreams(
        preferredVideo: BiliPlayStreamResource?,
        preferredAudio: BiliPlayStreamResource?,
        dash: BiliPlayStreamDash,
        selector: (
            BiliPlayStreamResource?,
            List<BiliPlayStreamResource>?
        ) -> BiliPlayStreamResource?
    ): StreamSelection? {
        val videoStream = selector(preferredVideo, dash.video)
        val audioStream = selector(preferredAudio, dash.getAllAudio())
        if (preferredVideo != null && videoStream == null) return null
        if (preferredAudio != null && audioStream == null) return null
        return StreamSelection(videoStream, audioStream)
    }
}
