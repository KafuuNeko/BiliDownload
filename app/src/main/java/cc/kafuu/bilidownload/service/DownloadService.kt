package cc.kafuu.bilidownload.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.entity.ResourceEntity
import cc.kafuu.bilidownload.common.jniexport.FFMpegJNI
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.manager.IDownloadStatusListener
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.notification.DownloadNotification
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.task.DownloadGroupTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

class DownloadService : Service(), IDownloadStatusListener {
    companion object {
        private const val TAG = "DownloadService"

        fun startDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadService::class.java)
            intent.putExtra("taskId", taskId)
            context.startService(intent)
        }
    }

    private val mServiceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mDownloadTaskDao = CommonLibs.requireAppDatabase().downloadTaskDao()
    private val mResourceDao = CommonLibs.requireAppDatabase().resourceDao()

    private var mRunningTaskCount by Delegates.observable(0) { _, _, value ->
        if (value > 0) return@observable
        stopSelf()
    }

    private lateinit var mDownloadNotification: DownloadNotification

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        Aria.download(this).register()

        mDownloadNotification = DownloadNotification(this)

        DownloadManager.register(this)
        startForeground(
            mDownloadNotification.getChannelNotificationId(),
            mDownloadNotification.getForegroundNotification()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Aria.download(this).unRegister()
        DownloadManager.unregister(this)
        mServiceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra("taskId", -1L)
        if (taskId == null || taskId == -1L) {
            return super.onStartCommand(intent, flags, startId)
        }
        Log.d(TAG, "onStartCommand: $taskId")
        mRunningTaskCount++
        mServiceScope.launch { doRequestDownload(taskId) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 开始请求下载资源
     * @param taskId entity id
     * */
    private suspend fun doRequestDownload(taskId: Long) {
        mDownloadTaskDao.getDownloadTaskById(taskId)?.let {
            DownloadManager.requestDownload(it)
        } ?: {
            Log.e(TAG, "Task [E$taskId] get download task entity failed")
            mRunningTaskCount--
        }
    }

    /**
     * 请求下载资源失败 */
    override fun onRequestFailed(
        entity: DownloadTaskEntity,
        httpCode: Int,
        code: Int,
        message: String
    ) {
        mRunningTaskCount--
        mDownloadNotification.notificationRequestFailed(entity, httpCode, code, message)
    }

    /**
     * 下载状态改变 */
    override fun onDownloadStatusChange(
        entity: DownloadTaskEntity,
        task: DownloadGroupTask,
        status: DownloadManager.TaskStatus
    ) {
        Log.d(TAG, "Task [D${task.entity.id}, E${entity.id}] status change, status: $status")
        mServiceScope.launch {
            when (status) {
                DownloadManager.TaskStatus.PREPROCESSING_COMPLETED -> onPreprocessingCompleted(
                    entity,
                    task
                )
                DownloadManager.TaskStatus.FAILURE -> onDownloadFailed(entity, task)
                DownloadManager.TaskStatus.COMPLETED -> onDownloadCompleted(entity, task)
                DownloadManager.TaskStatus.EXECUTING -> onDownloadExecuting(entity, task)
                DownloadManager.TaskStatus.CANCELLED -> onDownloadCancelled(entity, task)
                else -> Unit
            }
            // 是终止态
            if (status.isEndStatus) {
                mDownloadNotification.updateDownloadProgress(entity, null)
                mRunningTaskCount--
            }
        }
    }

    private suspend fun onPreprocessingCompleted(
        entity: DownloadTaskEntity,
        task: DownloadGroupTask
    ) {
        mDownloadTaskDao.update(entity.apply {
            status = DownloadTaskEntity.STATUS_DOWNLOADING
        })
    }

    private suspend fun onDownloadFailed(entity: DownloadTaskEntity, task: DownloadGroupTask) {
        mDownloadTaskDao.update(entity.apply {
            status = DownloadTaskEntity.STATUS_DOWNLOAD_FAILED
        })
        mDownloadNotification.notificationDownloadFailed(entity)
    }

    private suspend fun onDownloadCompleted(entity: DownloadTaskEntity, task: DownloadGroupTask) {
        // 更新状态为正在合成
        mDownloadTaskDao.update(entity.apply {
            status = DownloadTaskEntity.STATUS_SYNTHESIS
        })

        val syntheticStatus = if (doSynthetic(entity, task)) {
            DownloadTaskEntity.STATUS_COMPLETED
        } else {
            DownloadTaskEntity.STATUS_SYNTHESIS_FAILED
        }

        mDownloadTaskDao.update(entity.apply {
            status = syntheticStatus
        })

        // 未完成的
        if (syntheticStatus == DownloadTaskEntity.STATUS_SYNTHESIS_FAILED) {
            mDownloadNotification.notificationSynthesisFailed(entity)
            return
        }

        // 登记资源
        val resource = entity.getDefaultOutputFile()!!
        mResourceDao.insert(
            ResourceEntity(
                taskEntityId = entity.id,
                name = "Default Resource",
                mimeType = entity.dashVideoMimeType,
                storageSizeBytes = resource.length(),
                creationTime = System.currentTimeMillis(),
                file = resource.path
            )
        )
    }

    /**
     * 尝试合成视频
     * */
    private fun doSynthetic(entity: DownloadTaskEntity, task: DownloadGroupTask): Boolean {
        val output = entity.getDefaultOutputFile()?.path ?: return false
        val caches = task.entity.subEntities.map { it.filePath }.toTypedArray()

        // 合成视频
        if (!FFMpegJNI.mergeMedia(output, caches)) {
            return false
        }

        // 清理缓存
        CommonLibs.requireDownloadCacheDir(entity.id).let {
            it.deleteRecursively()
            it.deleteOnExit()
        }

        return true
    }

    private fun onDownloadExecuting(entity: DownloadTaskEntity, task: DownloadGroupTask) {
        Log.d(
            TAG,
            "Task [D${task.entity.id}, E${entity.id}] status change, percent: ${task.percent}%"
        )
        mDownloadNotification.updateDownloadProgress(entity, task.percent)
    }

    private suspend fun onDownloadCancelled(entity: DownloadTaskEntity, task: DownloadGroupTask) {
        mDownloadTaskDao.delete(entity)
        mDownloadNotification.notificationDownloadCancel(entity)
    }
}