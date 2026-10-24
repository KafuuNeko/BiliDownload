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
import cc.kafuu.bilidownload.common.download.DownloadFailure
import cc.kafuu.bilidownload.common.download.DownloadGroupSnapshot
import cc.kafuu.bilidownload.common.download.DownloadTaskPostProcessor
import cc.kafuu.bilidownload.common.download.DownloadTaskRecoveryUseCase
import cc.kafuu.bilidownload.common.download.DownloadTaskRequestRegistry
import cc.kafuu.bilidownload.common.download.SynthesisRecoveryUseCase
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
import cc.kafuu.bilidownload.service.DownloadServiceTaskRegistry.Assignment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

/**
 * 持有唯一任务请求，协调媒体下载及串行后处理，直到任务实际结束才释放前台服务。
 *
 * 下载字节完成与任务完成是两个阶段；合成、可选删源、转封装和公共发布结束前继续持有任务身份。
 * 恢复任务复用同一后处理出口，避免已有成品因重新执行合成或过早释放服务而丢失发布机会。
 */
class DownloadService : Service() {
    companion object {
        private const val TAG = "DownloadService"

        private const val KEY_TASK_ID = "taskId"
        private const val KEY_REQUEST_TOKEN = "requestToken"
        private const val KEY_RETRY_ONLY = "retryOnly"

        private val mPostProcessor = DownloadTaskPostProcessor()
        private val mRequestRegistry = DownloadTaskRequestRegistry()

        /** 包含服务 Intent 尚未交付、详情同步、排队、下载及后处理的进程内任务身份。 */
        val inFlightTaskIds: StateFlow<Set<Long>> = mRequestRegistry.taskIds

        /** 服务请求与执行器共同决定任务是否在途，页面过滤不能替代服务侧校验。 */
        fun isTaskInFlight(taskId: Long): Boolean =
            mRequestRegistry.contains(taskId) || DownloadManager.containsTaskGroup(taskId)

        private fun startService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 启动新任务或继续中断任务；false 表示已有请求或下载组，未重复启动。 */
        fun startDownload(context: Context, taskId: Long): Boolean = submitTask(context, taskId, false)

        /** 发出仅允许失败状态的重试请求；true 仅表示服务启动请求已发送。 */
        fun retryTask(context: Context, taskId: Long): Boolean = submitTask(context, taskId, true)

        /** 先保留唯一身份再发送 Intent，发送失败立即回滚同一次请求。 */
        private fun submitTask(context: Context, taskId: Long, retryOnly: Boolean): Boolean {
            if (DownloadManager.containsTaskGroup(taskId)) return false
            val request = mRequestRegistry.reserve(taskId) ?: return false
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(KEY_TASK_ID, taskId)
                .putExtra(KEY_REQUEST_TOKEN, request.token)
                .putExtra(KEY_RETRY_ONLY, retryOnly)
            try {
                startService(context, intent)
                return true
            } catch (error: Exception) {
                mRequestRegistry.release(request)
                throw error
            }
        }

        /** 从持久化活动阶段恢复，已在途任务由公共提交入口跳过。 */
        suspend fun resumeDownload(context: Context) {
            DownloadRepository.queryDownloadTaskDetailByTaskId(
                TaskStatus.DOWNLOADING,
                TaskStatus.PREPARE,
                TaskStatus.SYNTHESIS,
                TaskStatus.PUBLISHING
            ).forEach {
                startDownload(context, it.id)
            }
        }
    }

    private val mServiceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 限制任务进入下载队列前的视频详情同步请求，批量入队时不会同时请求全部稿件。
    private val mAssignmentSemaphore = Semaphore(4)
    private val mTaskRegistry = DownloadServiceTaskRegistry(mRequestRegistry)
    @Volatile private var mIsDestroyed = false

    /** 仅释放仍属于本次请求的登记，后处理完成前不会停掉前台服务。 */
    private fun finishTask(assignment: Assignment) {
        mTaskRegistry.finish(assignment)?.let(::stopSelfResult)
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
        mIsDestroyed = true
        mServiceScope.cancel()
        // 已交给独立下载执行器的任务没有准备协程；释放服务身份，执行器仍负责自身去重。
        mTaskRegistry.snapshot().filter { it.handedToDownloader }.forEach(::finishTask)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val entityId = intent?.getLongExtra(KEY_TASK_ID, -1L)
        if (entityId == null || entityId == -1L) {
            return super.onStartCommand(intent, flags, startId)
        }
        // 进程重建后可能没有内存请求记录，按 Intent 身份重新登记；旧令牌不能冒领新请求。
        val token = intent.getLongExtra(KEY_REQUEST_TOKEN, -1L)
        val assignment = mTaskRegistry.tryRegister(entityId, token, startId)
        if (assignment == null) {
            Log.d(TAG, "Task [T$entityId] start is duplicate or stale")
            mTaskRegistry.stopIdIfIdle()?.let(::stopSelfResult)
            return super.onStartCommand(intent, flags, startId)
        }
        val retryOnly = intent.getBooleanExtra(KEY_RETRY_ONLY, false)
        // 原子启动保证服务刚销毁时也执行 finally，不留下永久在途身份。
        mServiceScope.launch(start = CoroutineStart.ATOMIC) { assigningTask(assignment, retryOnly) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 在原持久态上选择恢复阶段，只有执行器接管后才等待下载终态释放登记。 */
    private suspend fun assigningTask(assignment: Assignment, retryOnly: Boolean) {
        val useCase = DownloadTaskRecoveryUseCase(
            loadTask = { taskId ->
                DownloadRepository.getDownloadTaskByTaskId(taskId)?.also { assignment.task = it }
            },
            isDownloading = { task ->
                DownloadManager.containsTaskGroup(task.id) ||
                    task.groupId?.let(DownloadManager::containsTaskGroup) == true
            },
            download = ::prepareDownload,
            synthesize = { task ->
                mPostProcessor.execute {
                    // 恢复用例先识别已提交成品，再决定重合成或直接补齐后处理；两条路径共享同一把锁。
                    SynthesisRecoveryUseCase(
                        runSynthesis = { onDownloadCompleted(it, true) },
                        finishCompletedSynthesis = ::finishRecoveredSynthesis,
                    ).execute(task)
                }
            },
            publish = { task -> mPostProcessor.execute { publishTaskResources(task) } },
            onFailure = ::onRecoveryFailed,
        )
        try {
            // 前置请求保持既有并发上限，后处理在挂起期间也持有共享互斥锁。
            val result = mAssignmentSemaphore.withPermit {
                useCase.execute(assignment.request.taskId, retryOnly)
            }
            assignment.handedToDownloader = result == DownloadTaskRecoveryUseCase.Result.HANDED_TO_DOWNLOADER
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.e(TAG, "Task [T${assignment.request.taskId}] recovery failed")
        } finally {
            if (!assignment.handedToDownloader || mIsDestroyed) finishTask(assignment)
        }
    }

    /** 同步详情时保留原失败状态；删除或改变阶段的任务不再交给媒体执行器。 */
    private suspend fun prepareDownload(task: DownloadTaskEntity): Boolean = withContext(Dispatchers.IO) {
        doSaveVideoDetails(task)
        currentCoroutineContext().ensureActive()
        val current = DownloadRepository.getDownloadTaskByTaskId(task.id) ?: return@withContext false
        if (DownloadTaskRecoveryUseCase.phaseFor(current.status) != DownloadTaskRecoveryUseCase.Phase.DOWNLOAD) {
            return@withContext false
        }
        DownloadManager.requestDownload(task)
        true
    }

    /** 获取详情失败时只清理尚未建立有效信息的新任务，不重置或删除失败任务缓存。 */
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
            Log.e(TAG, "Task [T${task.id}] details failed, http=$responseCode, code=$returnCode")
        }?.let {
            // 更新数据库中的视频信息
            BiliVideoRepository.doInsertOrUpdateVideoDetails(it, task.biliCid)
        } ?: throw IllegalStateException("Task [T${task.id}] get video details failed")
    }

    /** 保存实际失败阶段并提供通用脱敏反馈，缺失记录不被重建。 */
    private suspend fun onRecoveryFailed(task: DownloadTaskEntity, phase: DownloadTaskRecoveryUseCase.Phase) {
        if (DownloadRepository.getDownloadTaskByTaskId(task.id) == null) return
        DownloadRepository.update(task.apply { status = phase.failureStatus.code })
        when (phase) {
            DownloadTaskRecoveryUseCase.Phase.DOWNLOAD -> mDownloadNotification.notificationDownloadFailed(
                task, DownloadFailure(DownloadFailure.Kind.SOURCE_UNAVAILABLE)
            )
            DownloadTaskRecoveryUseCase.Phase.SYNTHESIS -> mDownloadNotification.notificationSynthesisFailed(task)
            DownloadTaskRecoveryUseCase.Phase.PUBLISH -> mDownloadNotification.notificationPublishFailed(task, "")
        }
        Log.e(TAG, "Task [T${task.id}] recovery failed, phase=$phase")
    }


    /**
     * 请求下载资源失败事件 */
    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onRequestFailedEvent(event: DownloadRequestFailedEvent) {
        val assignment = mTaskRegistry.find(event.task.id) ?: return
        mServiceScope.launch {
            assignment.eventMutex.withLock {
                if (!mTaskRegistry.owns(assignment, event.task)) return@withLock
                try {
                    DownloadRepository.update(event.task.apply { status = TaskStatus.DOWNLOAD_FAILED.code })
                    mDownloadNotification.notificationRequestFailed(
                        event.task, event.httpCode, event.code, event.message
                    )
                } finally {
                    finishTask(assignment)
                }
            }
        }
    }

    /**
     * 下载状态改变事件 */
    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onDownloadStatusChangeEvent(event: DownloadStatusChangeEvent) {
        val assignment = mTaskRegistry.find(event.task.id) ?: return
        mServiceScope.launch {
            // 同一请求的状态事件串行消费，结束后到达的进度不能覆盖最终状态。
            assignment.eventMutex.withLock {
                if (!mTaskRegistry.owns(assignment, event.task)) return@withLock
                try {
                    when (event.status) {
                        DownloadStatus.COMPLETED -> completeDownloadedTask(event.task)
                        DownloadStatus.FAILURE -> onDownloadFailed(event.task, event.group)
                        DownloadStatus.EXECUTING -> onDownloadExecuting(event.task, event.group)
                        DownloadStatus.CANCELLED -> onDownloadCancelled(event.task, event.group)
                        DownloadStatus.STOPPED -> onDownloadStopped(event.task, event.group)
                        else -> Unit
                    }
                } finally {
                    if (event.status.isEndStatus || event.status == DownloadStatus.STOPPED) {
                        mDownloadNotification.updateDownloadProgress(event.task, null)
                        finishTask(assignment)
                    }
                }
            }
        }
    }

    /** 下载完成后串行处理合成和发布，服务身份一直保留到后处理返回。 */
    private suspend fun completeDownloadedTask(task: DownloadTaskEntity) = mPostProcessor.execute {
        try {
            onDownloadCompleted(task)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onRecoveryFailed(task, DownloadTaskRecoveryUseCase.phaseFor(task.status)
                ?: DownloadTaskRecoveryUseCase.Phase.DOWNLOAD)
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
        mDownloadNotification.notificationDownloadFailed(task, group.failure)
    }

    /**
     * 把下载完成或经校验可重合成的任务推进到媒体后处理阶段。
     *
     * 调用方须处于 mPostProcessor 的串行边界内。先登记源资源，双流任务先持久化合成状态，
     * 成功后再统一进入转封装与发布；这里只在合成失败时直接保存失败状态。
     *
     * @param task 原任务对象，阶段提交可能同步更新其 status。
     * @param isResume 是否来自恢复入口；为 true 时允许重新进入合成，由恢复用例负责前置资源核验。
     * */
    private suspend fun onDownloadCompleted(
        task: DownloadTaskEntity,
        isResume: Boolean = false
    ) {
        val currentStatus = DownloadRepository.getDownloadTaskByTaskId(task.id)?.status?.let { c ->
            TaskStatus.entries.find { it.code == c }
        } ?: return
        val dashEntityList = DownloadRepository.queryDashList(task)

        // 正常下载回调不能重复处理正在合成或已完成的任务；显式恢复已提前做过阶段与资源核验。
        if (!isResume &&
            (currentStatus == TaskStatus.SYNTHESIS || currentStatus == TaskStatus.COMPLETED)
        ) {
            Log.e(
                TAG,
                "Task [T${task.id}] The current state cannot perform the synthesis operation, status: $currentStatus"
            )
            return
        }

        // 首次下载完成时登记工作文件；恢复已有任务则沿用资源记录，不创建重复源记录。
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

        // 优先使用已登记的源路径，兼容工作文件名变化；缺少登记时才使用 Dash 路径回退。
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
            // 合成执行成功只代表媒体成品就绪，最终 COMPLETED 必须由后续发布出口决定。
            finishDownloadedResources(task, isMergedMediaTask)
            return
        }

        // 当前未进入发布出口，保存的是本次合成失败状态，保留源文件供后续恢复。
        DownloadRepository.update(task.apply { status = finalStatus.code })
    }

    /**
     * 复用恢复用例已核验的合成输出，补齐可能中断的源清理和发布流程。
     *
     * 不能再次清理 MIXED 或进入 FFmpeg 合成，否则源已按设置删除时会失去唯一有效成品。
     * 剩余源文件删除失败只记录错误并保留失败记录，成品仍继续交给后处理出口。
     */
    private suspend fun finishRecoveredSynthesis(task: DownloadTaskEntity) {
        if (AppModel.deleteSourceFilesAfterMerge) {
            if (!DownloadRepository.deleteDownloadTaskSourceResources(task.id)) {
                Log.e(TAG, "Unable to delete one or more source resources for task ${task.id}")
            }
        }
        finishDownloadedResources(task, isMergedMediaTask = true)
    }

    /**
     * 为正常完成和合成恢复提供相同的转封装、发布出口，由发布结果提交最终任务状态。
     *
     * 已选择合成后删源的双流任务跳过源音频转封装；其余资源沿用当前偏好和存储策略。
     * 方法运行在调用方持有的串行后处理边界内，不另行启动脱离服务生命周期的后台任务。
     */
    private suspend fun finishDownloadedResources(task: DownloadTaskEntity, isMergedMediaTask: Boolean) {
        tryAutoRemuxAudioResources(
            task = task,
            skipSourceResources = isMergedMediaTask && AppModel.deleteSourceFilesAfterMerge,
        )
        publishTaskResources(task)
    }

    /**
     * 把下载完成的任务推进到最终发布状态。
     *
     * 内部存储任务可直接完成；公共存储任务先标记为 `PUBLISHING`，全部资源发布成功后才
     * 标记为 `COMPLETED`。任一资源失败都会保留可恢复信息并进入 `PUBLISH_FAILED`。
     */
    private suspend fun publishTaskResources(task: DownloadTaskEntity) {
        val isPublishingRetry = task.status == TaskStatus.PUBLISHING.code ||
            task.status == TaskStatus.PUBLISH_FAILED.code
        if (
            !isPublishingRetry &&
            AppModel.downloadPathMode == cc.kafuu.bilidownload.common.model.DownloadPathMode.INTERNAL
        ) {
            DownloadRepository.update(task.apply { status = TaskStatus.COMPLETED.code })
            mDownloadNotification.notificationDownloadCompleted(task)
            return
        }

        DownloadRepository.update(task.apply { status = TaskStatus.PUBLISHING.code })
        val result = DownloadRepository.publishResourcesIfNeeded(task.id, forcePublish = true)
        if (result.isSuccess) {
            DownloadRepository.update(task.apply { status = TaskStatus.COMPLETED.code })
            mDownloadNotification.notificationDownloadCompleted(task)
            return
        }

        DownloadRepository.update(task.apply { status = TaskStatus.PUBLISH_FAILED.code })
        val failureSummary = result.failures.joinToString { failure ->
            "resource ${failure.resourceId}: ${failure.reason}"
        }
        Log.e(TAG, "Task [T${task.id}] publishing failed: $failureSummary")
        mDownloadNotification.notificationPublishFailed(task, failureSummary)
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
        mDownloadNotification.updateDownloadProgress(task, group.percent, group.retry)
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
    }

    /**
     * 预登记独立输出后同步合成，成功时提交成品尺寸，再按偏好清理音视频源。
     *
     * 非零尺寸的持久化记录是恢复用例识别完成输出的检查点，必须先于删源提交。
     * 原生失败时只清理本次预登记输出，不能触碰仍用于重试的源资源。
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
            // 先落库实际尺寸再删源；中断后恢复用例才能区分已完成成品与预登记的半成品。
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
