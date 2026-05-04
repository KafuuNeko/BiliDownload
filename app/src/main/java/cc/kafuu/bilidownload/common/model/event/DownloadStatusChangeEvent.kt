package cc.kafuu.bilidownload.common.model.event

import cc.kafuu.bilidownload.common.model.DownloadStatus
import cc.kafuu.bilidownload.common.download.DownloadGroupSnapshot
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity

/**
 * 下载执行器向业务层发布的状态事件
 */
class DownloadStatusChangeEvent(
    task: DownloadTaskEntity,
    val group: DownloadGroupSnapshot,
    val status: DownloadStatus
): DownloadTaskEvent(task)
