package cc.kafuu.bilidownload.common.download

import cc.kafuu.bilidownload.common.model.DownloadStatus

/**
 * 下载组在某一时刻的只读状态。
 *
 * 一个下载任务可能包含多个实际资源，例如 DASH 视频流和音频流。UI 和 Service 不直接感知
 * 每个子资源的细节，只通过这个快照读取聚合后的进度与状态。
 */
data class DownloadGroupSnapshot(
    val id: Long,
    val status: DownloadStatus,
    val percent: Int,
    val currentProgress: Long,
    val fileSize: Long
) {
    val isComplete: Boolean
        get() = status == DownloadStatus.COMPLETED
}
