package cc.kafuu.bilidownload.common.model.event

import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import com.arialyy.aria.core.task.DownloadGroupTask

class DownloadStatusChangeEvent(
    entity: DownloadTaskEntity,
    val task: DownloadGroupTask,
    val status: DownloadManager.TaskStatus
): DownloadTaskEvent(entity)