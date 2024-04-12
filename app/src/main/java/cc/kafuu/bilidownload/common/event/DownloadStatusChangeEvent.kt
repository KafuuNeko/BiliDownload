package cc.kafuu.bilidownload.common.event

import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import com.arialyy.aria.core.task.DownloadGroupTask

data class DownloadStatusChangeEvent(
    val entity: DownloadTaskEntity,
    val task: DownloadGroupTask,
    val status: Int
)
