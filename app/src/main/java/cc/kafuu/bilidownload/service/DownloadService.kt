package cc.kafuu.bilidownload.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import cc.kafuu.bilidownload.common.jniexport.FFMpegJNI
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.manager.IDownloadStatusListener
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.room.entity.BiliVideoMainEntity
import cc.kafuu.bilidownload.common.room.entity.BiliVideoPartEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.entity.ResourceEntity
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
            intent.putExtra("entityId", taskId)
            context.startService(intent)
        }
    }

    private val mServiceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val mDownloadTaskDao = CommonLibs.requireAppDatabase().downloadTaskDao()
    private val mBiliVideoDao = CommonLibs.requireAppDatabase().biliVideoDao()
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
        val entityId = intent?.getLongExtra("entityId", -1L)
        if (entityId == null || entityId == -1L) {
            return super.onStartCommand(intent, flags, startId)
        }
        Log.d(TAG, "onStartCommand: $entityId")
        mRunningTaskCount++
        mServiceScope.launch {
            try {
                assigningTask(entityId)
            } catch (e: Exception) {
                mRunningTaskCount--
                Log.e(TAG, e.message ?: "Unknown error")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 开始分配任务
     * @param entityId entity id
     * */
    private suspend fun assigningTask(entityId: Long) {
        val entity = mDownloadTaskDao.getDownloadTaskById(entityId)
            ?: throw IllegalStateException("Task [E$entityId] get download task entity failed")

        doSaveVideoDetailsAndToRequestDownload(entity)
    }

    /**
     * 尝试保存视频信息和请求下载
     * 1. 请求获取视频详情；2. 根据获取到的数据更新数据库；3.视频信息保存后请求下载视频。
     * */
    private suspend fun doSaveVideoDetailsAndToRequestDownload(entity: DownloadTaskEntity) {
        val biliVideoData =
            NetworkManager.biliVideoRepository.syncGetVideoDetail(entity.biliBvid) { responseCode, returnCode, message ->
                mDownloadNotification.notificationGetVideoDetailsFailed(
                    entity,
                    responseCode,
                    returnCode,
                    message
                )
                // 如果无法获取视频详情，且这个任务还在准备阶段则直接删除任务
                // 因为没有对应获取视频详情失败的STATUS（也不需要）
                if (entity.status == DownloadTaskEntity.STATUS_PREPARE) {
                    mServiceScope.launch { mDownloadTaskDao.delete(entity) }
                }
                Log.e(
                    TAG,
                    "Task [E${entity.id}] get video details failed, responseCode: $responseCode, returnCode: $returnCode, message: $message"
                )
            } ?: throw IllegalStateException("Task [E${entity.id}] get video details failed")

        // Log.d(TAG, "Task [E${entity.id}] get video details: $biliVideoData")
        // 插入或更新bv视频信息
        val biliVideoMainEntity = BiliVideoMainEntity(
            biliVideoData.bvid,
            biliVideoData.title,
            biliVideoData.desc,
            biliVideoData.pic
        )
        mBiliVideoDao.insert(biliVideoMainEntity)
        Log.d(
            TAG,
            "Task [E${entity.id}] insert or update biliVideoMainEntity: $biliVideoMainEntity"
        )

        // 插入或更新次bv的所有子视频信息
        val biliVideoPartEntityList = biliVideoData.pages.map {
            BiliVideoPartEntity(
                biliVideoData.bvid,
                it.cid,
                it.part
            )
        }
        mBiliVideoDao.insert(*biliVideoPartEntityList.toTypedArray())
        Log.d(
            TAG,
            "Task [E${entity.id}] insert or update biliVideoMainEntity: $biliVideoPartEntityList"
        )

        // 保存完视频信息后开始请求下载
        DownloadManager.requestDownload(entity)
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

    private suspend fun onDownloadExecuting(entity: DownloadTaskEntity, task: DownloadGroupTask) {
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