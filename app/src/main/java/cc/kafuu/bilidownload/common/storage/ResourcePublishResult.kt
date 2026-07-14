package cc.kafuu.bilidownload.common.storage

import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity

/** 单个下载资源的发布结果；失败会被值化，调用方无需依赖异常控制任务状态。 */
sealed interface ResourcePublishResult {
    /** 资源无需发布或已经成功发布，[resource] 是应继续使用和展示的最新记录。 */
    data class Success(val resource: DownloadResourceEntity) : ResourcePublishResult

    /**
     * 资源发布失败。
     *
     * [resourceId] 用于定位失败项，[reason] 用于日志和通知，[cause] 保留原始异常供诊断。
     */
    data class Failure(
        val resourceId: Long,
        val reason: String,
        val cause: Throwable
    ) : ResourcePublishResult
}

/** 一个下载任务下全部资源的批量发布结果。 */
data class ResourcePublishBatchResult(
    val publishedResources: List<DownloadResourceEntity>,
    val failures: List<ResourcePublishResult.Failure>
) {
    /** 仅当没有任何资源失败时为 `true`。 */
    val isSuccess: Boolean
        get() = failures.isEmpty()
}
