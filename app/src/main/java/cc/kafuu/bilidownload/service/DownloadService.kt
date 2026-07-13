package cc.kafuu.bilidownload.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.download.DownloadGroupSnapshot
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.DownloadStatus
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.event.DownloadRequestFailedEvent
import cc.kafuu.bilidownload.common.model.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.BiliVideoRepository
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.storage.ResourceStorage
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.FFMpegUtils
import cc.kafuu.bilidownload.notification.DownloadNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.util.concurrent.Executors
import kotlin.properties.Delegates

class DownloadService : Service() {
    companion object {
        private const val TAG = "DownloadService"

        private const val KEY_TASK_ID = "taskId"

        private val mTaskFinishedExecutor = Executors.newSingleThreadExecutor()

        private fun startService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadService::class.java)
            intent.putExtra(KEY_TASK_ID, taskId)
            startService(context, intent)
        }

        suspend fun resumeDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            DownloadRepository.queryDownloadTaskDetailByTaskId(
                TaskStatus.DOWNLOADING,
                TaskStatus.PREPARE,
                TaskStatus.SYNTHESIS
            ).forEach {
                if (it.groupId != null && !DownloadManager.containsTaskGroup(it.groupId!!)) {
                    intent.putExtra(KEY_TASK_ID, it.id)
                    startService(context, intent)
                }
            }
        }
    }

    private val mServiceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var mRunningTaskCount by Delegates.observable(0) { _, _, value ->
        if (value > 0) return@observable
        stopSelf()
    }

    private lateinit var mDownloadNotification: DownloadNotification

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        mDownloadNotification = DownloadNotification(this)

        EventBus.getDefault().register(this)
        startForeground(
            AppModel.FIXED_NOTIFICATION_ID,
            mDownloadNotification.getForegroundNotification()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        mServiceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val entityId = intent?.getLongExtra(KEY_TASK_ID, -1L)
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
     * 分配下载任务
     * @param taskId 下载任务ID
     * */
    private suspend fun assigningTask(taskId: Long) {
        val taskEntity = DownloadRepository.getDownloadTaskByTaskId(taskId)
            ?: throw IllegalStateException("Task [T$taskId] get download task entity failed")

        // 如果此任务处于合成状态，则可能是因为此任务在合成执行过程中应用退出导致合成中断
        if (taskEntity.status == TaskStatus.SYNTHESIS.code) {
            // 清理资源
            DownloadRepository.deleteDownloadTaskMixedResource(taskId)
            // 重新走任务下载完成流程
            onDownloadCompleted(taskEntity, true)
            return
        }

        if (taskEntity.groupId?.let { DownloadManager.containsTaskGroup(it) } == true) {
            mRunningTaskCount--
            return
        }

        // 尝试保存视频信息和请求下载
        doSaveVideoDetails(taskEntity)

        // 开始请求下载
        DownloadManager.requestDownload(taskEntity)
    }

    /**
     * 尝试保存视频信息
     * previous: [assigningTask]
     * 1. 请求获取视频详情；2. 根据获取到的数据更新数据库；
     * */
    private suspend fun doSaveVideoDetails(task: DownloadTaskEntity) {
        NetworkManager.biliVideoRepository.syncRequestVideoDetail(task.biliBvid) { responseCode, returnCode, message ->
            mDownloadNotification.notificationGetVideoDetailsFailed(
                task,
                responseCode, returnCode, message
            )
            // 如果无法获取视频详情，且这个任务还在准备阶段则直接删除任务
            // 因为没有对应获取视频详情失败的STATUS（也不需要）
            if (task.status == TaskStatus.PREPARE.code) {
                mServiceScope.launch { DownloadRepository.deleteDownloadTask(task.id) }
            }
            Log.e(
                TAG,
                "Task [T${task.id}] get video details failed, responseCode: $responseCode, returnCode: $returnCode, message: $message"
            )
        }?.let {
            // 更新数据库中的视频信息
            BiliVideoRepository.doInsertOrUpdateVideoDetails(it, task.biliCid)
        } ?: throw IllegalStateException("Task [T${task.id}] get video details failed")
    }


    /**
     * 请求下载资源失败事件 */
    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onRequestFailedEvent(event: DownloadRequestFailedEvent) {
        mRunningTaskCount--
        mDownloadNotification.notificationRequestFailed(
            event.task,
            event.httpCode, event.code, event.message
        )
    }

    /**
     * 下载状态改变事件 */
    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onDownloadStatusChangeEvent(event: DownloadStatusChangeEvent) {
        Log.d(
            TAG,
            "Task [G${event.group.id}, T${event.task.id}] status change, status: ${event.status}"
        )
        mServiceScope.launch {
            when (event.status) {
                DownloadStatus.COMPLETED -> mTaskFinishedExecutor.execute {
                    runBlocking { onDownloadCompleted(event.task) }
                }

                DownloadStatus.FAILURE -> onDownloadFailed(event.task, event.group)
                DownloadStatus.EXECUTING -> onDownloadExecuting(event.task, event.group)
                DownloadStatus.CANCELLED -> onDownloadCancelled(event.task, event.group)
                DownloadStatus.STOPPED -> onDownloadStopped(event.task, event.group)
                else -> Unit
            }
            // 是终止态
            if (event.status.isEndStatus) {
                mDownloadNotification.updateDownloadProgress(event.task, null)
                mRunningTaskCount--
            }
        }
    }


    /**
     * 下载失败
     * from [onDownloadStatusChangeEvent]
     * */
    private suspend fun onDownloadFailed(task: DownloadTaskEntity, group: DownloadGroupSnapshot) {
        DownloadRepository.update(task.apply {
            status = TaskStatus.DOWNLOAD_FAILED.code
        })
        mDownloadNotification.notificationDownloadFailed(task)
    }

    /**
     * 完成下载操作
     * from [onDownloadStatusChangeEvent]
     * */
    private suspend fun onDownloadCompleted(
        task: DownloadTaskEntity,
        isResume: Boolean = false
    ) {
        val currentStatus = DownloadRepository.getDownloadTaskByTaskId(task.id)?.status?.let { c ->
            TaskStatus.entries.find { it.code == c }
        } ?: return
        val dashEntityList = DownloadRepository.queryDashList(task)

        // 如果来源不是任务恢复则检查当前的状态是否为正在合成或者完成状态，若是则不执行
        if (!isResume &&
            (currentStatus == TaskStatus.SYNTHESIS || currentStatus == TaskStatus.COMPLETED)
        ) {
            Log.e(
                TAG,
                "Task [T${task.id}] The current state cannot perform the synthesis operation, status: $currentStatus"
            )
            return
        }

        // 如果当前任务处于正在下载状态，则将Dash资源注册
        val registeredSourceResources = if (currentStatus == TaskStatus.DOWNLOADING) {
            dashEntityList.map { DownloadRepository.registerResource(task, it) }
        } else {
            emptyList()
        }
        val sourceResources = registeredSourceResources.ifEmpty {
            DownloadRepository.queryResourcesForExport(task.id)
                .filter {
                    it.type == DownloadResourceType.VIDEO ||
                        it.type == DownloadResourceType.AUDIO
                }
        }

        // 取得Dash资源执行合并操作
        val videoDash = dashEntityList.find { it.type == DashType.VIDEO }
        val audioDash = dashEntityList.find { it.type == DashType.AUDIO }
        val videoResourceFile = sourceResources.find {
            it.type == DownloadResourceType.VIDEO
        }?.file?.let { File(it) }
        val audioResourceFile = sourceResources.find {
            it.type == DownloadResourceType.AUDIO
        }?.file?.let { File(it) }

        var isMergedMediaTask = false
        val finalStatus = if (dashEntityList.size == 2 && videoDash != null && audioDash != null) {
            isMergedMediaTask = true
            // 立即更新状态为正在合成
            DownloadRepository.update(task.apply { status = TaskStatus.SYNTHESIS.code })
            // 执行音视频合成逻辑
            doMergeMediaTask(
                task = task,
                videoDash = videoDash,
                videoSourceFile = videoResourceFile ?: resolveDashOutputFile(videoDash),
                audioSourceFile = audioResourceFile ?: resolveDashOutputFile(audioDash)
            )
        } else {
            TaskStatus.COMPLETED
        }

        if (finalStatus == TaskStatus.COMPLETED) {
            tryAutoRemuxAudioResources(
                task = task,
                skipSourceResources = isMergedMediaTask && AppModel.deleteSourceFilesAfterMerge
            )
            if (!DownloadRepository.publishResourcesIfNeeded(task.id)) {
                Log.e(TAG, "Some resources could not be published to shared Downloads storage")
            }
        }

        // 更新记录为最终状态
        DownloadRepository.update(task.apply { status = finalStatus.code })
    }

    /**
     * 尝试将保留的音频源资源无损转封装为音频专用格式。
     */
    private suspend fun tryAutoRemuxAudioResources(
        task: DownloadTaskEntity,
        skipSourceResources: Boolean
    ) {
        if (!AppModel.autoRemuxAudioAfterDownload || skipSourceResources) return

        DownloadRepository.queryResourcesForExport(task.id)
            .filter { it.type == DownloadResourceType.AUDIO }
            .forEach { tryAutoRemuxAudioResource(it) }
    }

    private suspend fun tryAutoRemuxAudioResource(resource: DownloadResourceEntity) {
        val sourceFile = File(resource.file)
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return

        val remuxFormat = FFMpegUtils.detectAudioRemuxFormat(sourceFile.path) ?: return
        if (sourceFile.extension.equals(remuxFormat.suffix, ignoreCase = true)) {
            updateAudioResourceMimeTypeIfNeeded(resource, remuxFormat.mimeType)
            return
        }

        val parentFile = sourceFile.parentFile ?: return
        val targetFile = DownloadFileNameUtils.buildUniqueFile(
            directory = parentFile,
            baseName = sourceFile.nameWithoutExtension,
            extension = remuxFormat.suffix
        )

        val isSuccess = FFMpegUtils.remuxAudio(
            sourceFile = sourceFile.path,
            targetFile = targetFile.path,
            format = remuxFormat
        )
        if (!isSuccess) {
            Log.d(TAG, "Audio remux failed, keep original file: ${sourceFile.path}")
            return
        }

        try {
            val remuxedResource = resource.copy(
                mimeType = remuxFormat.mimeType,
                storageSizeBytes = targetFile.length(),
                name = DownloadRepository.getResourceNameForFile(
                    downloadResourceType = DownloadResourceType.AUDIO,
                    resourceFile = targetFile,
                    fallbackName = resource.name
                ),
                file = targetFile.path,
                contentUri = null
            )
            DownloadRepository.updateOrInsertResource(remuxedResource)
            if (!ResourceStorage.delete(resource)) {
                DownloadRepository.updateOrInsertResource(resource)
                ResourceStorage.deleteFile(targetFile, remuxFormat.mimeType)
                Log.e(TAG, "Delete original audio file failed: ${sourceFile.path}")
            }
        } catch (e: Exception) {
            ResourceStorage.deleteFile(targetFile, remuxFormat.mimeType)
            Log.e(TAG, "Update remuxed audio resource failed", e)
        }
    }

    private suspend fun updateAudioResourceMimeTypeIfNeeded(
        resource: DownloadResourceEntity,
        mimeType: String
    ) {
        if (resource.mimeType.equals(mimeType, ignoreCase = true)) return
        DownloadRepository.updateOrInsertResource(resource.copy(mimeType = mimeType))
    }

    /**
     * 执行音视频合并逻辑
     */
    private suspend fun doMergeMediaTask(
        task: DownloadTaskEntity,
        videoDash: DownloadDashEntity,
        videoSourceFile: File,
        audioSourceFile: File
    ): TaskStatus {
        // 如果合成失败将在一百毫秒后重新将尝试合成，如果再次失败则音视频合成失败
        repeat(2) {
            if (tryMergeVideo(task, videoDash, videoSourceFile, audioSourceFile)) {
                return TaskStatus.COMPLETED
            }
            delay(100)
        }
        mDownloadNotification.notificationSynthesisFailed(task)
        return TaskStatus.SYNTHESIS_FAILED
    }

    /**
     * 下载任务执行中...
     * from [onDownloadStatusChangeEvent]*/
    private suspend fun onDownloadExecuting(task: DownloadTaskEntity, group: DownloadGroupSnapshot) {
        Log.d(
            TAG,
            "Task [G${group.id}, T${task.id}] status change, percent: ${group.percent}%"
        )
        if (task.status != TaskStatus.DOWNLOADING.code) {
            DownloadRepository.update(task.apply {
                status = TaskStatus.DOWNLOADING.code
            })
        }
        mDownloadNotification.updateDownloadProgress(task, group.percent)
    }

    /**
     * 下载任务被取消
     * from [onDownloadStatusChangeEvent] */
    private suspend fun onDownloadCancelled(task: DownloadTaskEntity, group: DownloadGroupSnapshot) {
        DownloadRepository.deleteDownloadTask(task.id)
        mDownloadNotification.notificationDownloadCancel(task)
    }

    /**
     * 下载任务被暂停
     * from [onDownloadStatusChangeEvent] */
    private fun onDownloadStopped(task: DownloadTaskEntity, group: DownloadGroupSnapshot) {
        mDownloadNotification.updateDownloadProgress(task, null)
        mRunningTaskCount--
    }

    /**
     * @brief 尝试合成视频
     * 如果视频合成成功后自动登记资源
     * */
    private suspend fun tryMergeVideo(
        task: DownloadTaskEntity,
        videoDash: DownloadDashEntity,
        videoSourceFile: File,
        audioSourceFile: File
    ): Boolean {
        val outputFile = DownloadRepository.buildMixedResourceOutputFile(
            downloadTaskEntity = task,
            mimeType = videoDash.mimeType,
            fallbackBaseName = "merge-${task.id}-${System.currentTimeMillis()}"
        )
        val resourceName = DownloadRepository.getResourceNameForFile(
            downloadResourceType = DownloadResourceType.MIXED,
            resourceFile = outputFile,
            fallbackName = "Merge Resource"
        )
        // 预登记合成资源
        val resourceEntity = DownloadRepository.registerResource(
            downloadTaskId = task.id,
            resourceName = resourceName,
            downloadResourceType = DownloadResourceType.MIXED,
            resourceFile = outputFile,
            mimeType = videoDash.mimeType
        )
        // 尝试合成
        val isSuccess = FFMpegUtils.mergeMedia(
            videoSourceFile.path,
            audioSourceFile.path,
            outputFile.path
        )
        if (isSuccess) {
            // 合成成功，更新登记资源尺寸
            resourceEntity.copy(
                storageSizeBytes = outputFile.length(),
            ).run {
                DownloadRepository.updateOrInsertResource(this)
            }
            if (AppModel.deleteSourceFilesAfterMerge) {
                if (!DownloadRepository.deleteDownloadTaskSourceResources(task.id)) {
                    Log.e(TAG, "Unable to delete one or more source resources for task ${task.id}")
                }
            }
        } else {
            // 如果合成失败则清理合成输出资源
            if (!DownloadRepository.deleteResource(resourceEntity)) {
                Log.e(TAG, "Unable to clean failed merge output: ${outputFile.path}")
            }
        }
        return isSuccess
    }

    private fun resolveDashOutputFile(dash: DownloadDashEntity): File {
        val workingFile = dash.getOutputFile()
        if (workingFile.exists()) return workingFile
        return dash.getLegacyOutputFile().takeIf { it.exists() } ?: workingFile
    }
}
