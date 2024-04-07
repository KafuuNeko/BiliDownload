package cc.kafuu.bilidownload.common.manager

import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import com.arialyy.aria.core.task.DownloadGroupTask

interface IDownloadStatusListener {
    fun onRequestFailed(entity: DownloadTaskEntity, httpCode: Int, code: Int, message: String)
    fun onDownloadStatusChange(entity: DownloadTaskEntity, task: DownloadGroupTask, status: DownloadManager.Companion.TaskStatus)
}