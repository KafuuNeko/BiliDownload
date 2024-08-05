package cc.kafuu.bilidownload.common.model.event

import cc.kafuu.bilidownload.common.model.DownloadTaskStatus
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import com.arialyy.aria.core.task.DownloadGroupTask

class DownloadStatusChangeEvent(
    task: DownloadTaskEntity,
    val group: DownloadGroupTask,
    val status: DownloadTaskStatus
): DownloadTaskEvent(task)