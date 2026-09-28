package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.util.Log
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.download.DownloadFailure
import cc.kafuu.bilidownload.common.download.DownloadGroupSnapshot
import cc.kafuu.bilidownload.common.download.DownloadRetry
import cc.kafuu.bilidownload.common.download.DownloadSourceSelector
import cc.kafuu.bilidownload.common.download.ResourceDownloadException
import cc.kafuu.bilidownload.common.download.ResourceDownloader
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.DownloadStatus
import cc.kafuu.bilidownload.common.model.DownloadSourceMode
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.model.event.DownloadRequestFailedEvent
import cc.kafuu.bilidownload.common.model.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.service.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * 一个数据库下载任务对应一个下载组，组内可以有一个或多个
 * DASH 资源。执行器负责请求真实流地址、把资源下载到缓存文件、聚合进度并通过 EventBus 通知
 * DownloadService 继续处理登记资源、合成音视频、通知用户等业务流程。
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private const val PROGRESS_INTERVAL_MS = 500L
    private const val MAX_CONCURRENT_TASKS = 3

    private val mCoroutineScope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

    // 运行中的任务组。key 使用 DownloadTaskEntity.id，避免再维护第三方库的任务 ID。
    private val mRunningTaskMap = ConcurrentHashMap<Long, RunningTask>()

    // 等待队列与正在请求播放地址的任务共同占用全局下载槽位，避免批量任务同时启动。
    private val mPendingTaskQueue = ConcurrentLinkedQueue<DownloadTaskEntity>()
    private val mPendingTaskIdSet =
        Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())
    private val mStartingTaskIdSet =
        Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())
    private val mStartingTaskStopStatusMap = ConcurrentHashMap<Long, DownloadStatus>()

    // 最近一次状态快照，供历史列表和详情页直接查询当前进度。
    private val mSnapshotMap = ConcurrentHashMap<Long, DownloadGroupSnapshot>()

    // 暂停状态需要跨一次任务取消保留下来，恢复下载时再清除。
    private val mPausedTaskSet = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    fun containsTaskGroup(groupId: Long) =
        mRunningTaskMap.containsKey(groupId) ||
            mPendingTaskIdSet.contains(groupId) ||
            mStartingTaskIdSet.contains(groupId)

    fun getSnapshot(groupId: Long) = mSnapshotMap[groupId]

    fun isStopped(groupId: Long) = mSnapshotMap[groupId]?.status == DownloadStatus.STOPPED ||
        mPausedTaskSet.contains(groupId)

    suspend fun startDownload(
        context: Context,
        bvid: String,
        cid: Long,
        resources: List<BiliDashModel>
    ): Boolean {
        val taskId = DownloadRepository.createNewRecordIfAbsent(
            bvid,
            cid,
            resources
        ) ?: return false
        DownloadService.startDownload(context, taskId)
        return true
    }

    fun cancelDownload(groupId: Long) {
        mPausedTaskSet.remove(groupId)
        stopRunningTask(groupId, DownloadStatus.CANCELLED)
    }

    fun stopDownload(groupId: Long) {
        mPausedTaskSet.add(groupId)
        stopRunningTask(groupId, DownloadStatus.STOPPED)
    }

    @Synchronized
    private fun stopRunningTask(groupId: Long, status: DownloadStatus) {
        mRunningTaskMap[groupId]?.let {
            it.requestedStopStatus = status
            it.job?.cancel()
            return
        }
        removePendingTask(groupId)?.let {
            publishWaitingTaskStatus(it, status)
            drainPendingTasks()
            return
        }
        if (mStartingTaskIdSet.contains(groupId)) {
            // 播放地址请求已经发出时先记录用户操作，待回调到达后再发布停止或取消事件。
            mStartingTaskStopStatusMap[groupId] = status
        }
    }

    suspend fun requestDownload(task: DownloadTaskEntity) {
        Log.d(TAG, "Task [T${task.id}] request download")
        val groupId = task.id
        if (containsTaskGroup(groupId)) return
        if (task.groupId != groupId) {
            DownloadRepository.update(task.apply { this.groupId = groupId })
        }
        mPausedTaskSet.remove(groupId)
        enqueueTask(task)
    }

    @Synchronized
    private fun enqueueTask(task: DownloadTaskEntity) {
        if (containsTaskGroup(task.id)) return
        mPendingTaskQueue.offer(task)
        mPendingTaskIdSet.add(task.id)
        drainPendingTasks()
    }

    /**
     * 在全局并发上限内启动等待任务。
     */
    @Synchronized
    private fun drainPendingTasks() {
        while (
            mRunningTaskMap.size + mStartingTaskIdSet.size < MAX_CONCURRENT_TASKS
        ) {
            val task = mPendingTaskQueue.poll() ?: return
            mPendingTaskIdSet.remove(task.id)
            if (!mStartingTaskIdSet.add(task.id)) continue
            try {
                requestPlayStream(task)
            } catch (e: Exception) {
                mStartingTaskIdSet.remove(task.id)
                EventBus.getDefault().post(
                    DownloadRequestFailedEvent(task, 0, 0, e.message ?: "unknown error")
                )
            }
        }
    }

    private fun requestPlayStream(task: DownloadTaskEntity) {
        // 下载前必须重新请求播放流，因为 B 站返回的真实资源 URL 可能过期。
        object : IServerCallback<BiliPlayStreamDash> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliPlayStreamDash
            ) {
                consumeStartingStopStatus(task)?.let {
                    publishWaitingTaskStatus(task, it)
                    drainPendingTasks()
                    return
                }
                onGetPlayStreamDashDone(task, httpCode, code, message, data)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                val stopStatus = finishStartingRequest(task)
                if (stopStatus != null) {
                    publishWaitingTaskStatus(task, stopStatus)
                    drainPendingTasks()
                    return
                }
                mStartingTaskIdSet.remove(task.id)
                Log.e(TAG, "onFailure: httpCode = $httpCode, code = $code, message = $message")
                EventBus.getDefault().post(
                    DownloadRequestFailedEvent(task, httpCode, code, message)
                )
                drainPendingTasks()
            }
        }.apply {
            NetworkManager.biliVideoRepository.requestPlayStreamDash(
                task.biliBvid,
                task.biliCid,
                this
            )
        }
    }

    @Synchronized
    private fun removePendingTask(groupId: Long): DownloadTaskEntity? {
        val task = mPendingTaskQueue.find { it.id == groupId } ?: return null
        if (!mPendingTaskQueue.remove(task)) return null
        mPendingTaskIdSet.remove(groupId)
        return task
    }

    @Synchronized
    private fun consumeStartingStopStatus(task: DownloadTaskEntity): DownloadStatus? {
        val status = mStartingTaskStopStatusMap.remove(task.id)
        if (status != null) mStartingTaskIdSet.remove(task.id)
        return status
    }

    @Synchronized
    private fun finishStartingRequest(task: DownloadTaskEntity): DownloadStatus? {
        mStartingTaskIdSet.remove(task.id)
        return mStartingTaskStopStatusMap.remove(task.id)
    }

    private fun publishWaitingTaskStatus(
        task: DownloadTaskEntity,
        status: DownloadStatus
    ) {
        val snapshot = DownloadGroupSnapshot(
            id = task.id,
            status = status,
            percent = 0,
            currentProgress = 0,
            fileSize = 0
        )
        mSnapshotMap[task.id] = snapshot
        EventBus.getDefault().post(DownloadStatusChangeEvent(task, snapshot, status))
    }

    fun onGetPlayStreamDashDone(
        task: DownloadTaskEntity,
        httpCode: Int,
        code: Int,
        message: String,
        data: BiliPlayStreamDash
    ) = mCoroutineScope.launch {
        try {
            // 将用户选择的 dashId/codecId 映射为本次可用的真实下载 URL。
            val requests = getDownloadResourceRequests(task, data)
            if (requests.isNotEmpty()) {
                doStartDownload(task, requests)
            } else {
                throw IllegalStateException("Task [G${task.groupId}] no resources available for download")
            }
        } catch (e: Exception) {
            val stopStatus = finishStartingRequest(task)
            if (stopStatus != null) {
                publishWaitingTaskStatus(task, stopStatus)
            } else {
                EventBus.getDefault().post(
                    DownloadRequestFailedEvent(task, httpCode, code, e.message ?: "unknown error")
                )
            }
            drainPendingTasks()
        }
    }

    /** 将全部已选资源映射为候选地址，任何资源缺失都不能把不完整的下载组当作成功。 */
    private suspend fun getDownloadResourceRequests(
        task: DownloadTaskEntity,
        dash: BiliPlayStreamDash
    ): List<ResourceRequest> {
        val resources = (dash.video ?: emptyList()) + dash.getAllAudio()
        // 全部资源匹配后再启动下载，防止只找到音频时误报整个视频任务完成。
        return coroutineScope {
            DownloadRepository.queryDashList(task).map { dashEntity ->
                async {
                    val resource = resources.find {
                        it.id == dashEntity.dashId && it.codecId == dashEntity.codecId
                    } ?: throw ResourceDownloadException(
                        DownloadFailure(DownloadFailure.Kind.SOURCE_UNAVAILABLE)
                    )
                    ResourceRequest(resource.selectStreamUrls(), dashEntity)
                }
            }.awaitAll()
        }
    }

    /** 保留所有源作为失败回退；自定义源探测不可用时优先回到接口提供的地址。 */
    private suspend fun BiliPlayStreamResource.selectStreamUrls(): List<String> {
        val candidates = getStreamUrls().filter { HttpUrl.parse(it) != null }
        val selector = DownloadSourceSelector(NetworkManager.downloadClient, ::buildRequest)
        return when (AppModel.downloadSourceMode) {
            DownloadSourceMode.AUTO_PROBE -> selector.rank(candidates)
            DownloadSourceMode.CUSTOM_HOST -> {
                val host = normalizeCustomHost(AppModel.downloadSourceCustomHost)
                val custom = host?.let { candidates.mapNotNull { url -> url.replaceHost(it) } }.orEmpty()
                selector.rank(custom, candidates)
            }
            else -> candidates
        }
    }

    /** 有界恢复中的地址刷新可取消，仍严格匹配原清晰度和编码，不静默降级。 */
    private suspend fun refreshResourceUrls(
        task: DownloadTaskEntity,
        selected: DownloadDashEntity
    ): List<String> {
        val dash = suspendCancellableCoroutine { continuation ->
            val call = NetworkManager.biliVideoRepository.requestPlayStreamDash(
                task.biliBvid, task.biliCid,
                object : IServerCallback<BiliPlayStreamDash> {
                    override fun onSuccess(httpCode: Int, code: Int, message: String, data: BiliPlayStreamDash) {
                        if (continuation.isActive) continuation.resume(data)
                    }

                    override fun onFailure(httpCode: Int, code: Int, message: String) {
                        if (!continuation.isActive) return
                        continuation.resumeWithException(ResourceDownloadException(
                            DownloadFailure(DownloadFailure.Kind.SOURCE_UNAVAILABLE, httpCode),
                            retryable = httpCode == 0 || httpCode in setOf(408, 429, 500, 502, 503, 504)
                        ))
                    }
                }
            )
            continuation.invokeOnCancellation { call.cancel() }
        }
        // 使用原资源身份重新选择候选，刷新结果不能替换为另一种清晰度或编码。
        val resource = (dash.video.orEmpty() + dash.getAllAudio()).find {
            it.id == selected.dashId && it.codecId == selected.codecId
        } ?: throw ResourceDownloadException(DownloadFailure(DownloadFailure.Kind.SOURCE_UNAVAILABLE))
        return resource.selectStreamUrls()
    }

    private fun normalizeCustomHost(host: String): String? {
        val value = host.trim().trimEnd('/')
        if (value.isBlank() || value.any { it.isWhitespace() }) return null

        val url = if (value.contains("://")) value else "https://$value"
        return HttpUrl.parse(url)?.host()
    }

    private fun String.replaceHost(host: String): String? {
        return HttpUrl.parse(this)
            ?.newBuilder()
            ?.host(host)
            ?.build()
            ?.toString()
    }

    /** 原子登记执行器，立即暂停也必须进入清理路径，释放队列槽位。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    private fun doStartDownload(task: DownloadTaskEntity, requests: List<ResourceRequest>) {
        val groupId = task.id
        mStartingTaskIdSet.remove(groupId)
        mStartingTaskStopStatusMap.remove(groupId)?.let {
            publishWaitingTaskStatus(task, it)
            drainPendingTasks()
            return
        }
        if (mRunningTaskMap.containsKey(groupId)) {
            drainPendingTasks()
            return
        }

        // groupId 保持写回数据库，旧 UI 和恢复流程仍可通过它定位下载组。
        task.groupId = groupId
        task.status = TaskStatus.DOWNLOADING.code

        val runningTask = RunningTask(task, requests)
        mRunningTaskMap[groupId] = runningTask
        // 同一锁内发布 Job；原子启动保证调度前取消也执行 runDownloadGroup 的 finally。
        runningTask.job = mCoroutineScope.launch(start = CoroutineStart.ATOMIC) {
            runDownloadGroup(runningTask)
        }

        Log.d(TAG, "Task [G${task.groupId}] start download")
    }

    /** 资源恢复耗尽后统一发布终态，所有退出路径都释放登记并推进等待队列。 */
    private suspend fun runDownloadGroup(runningTask: RunningTask) {
        val task = runningTask.task
        val progressMap = runningTask.requests.mapIndexed { index, _ ->
            index to PartProgress()
        }.toMap()
        runningTask.progressMap = progressMap

        try {
            DownloadRepository.update(task)
            publishSnapshot(runningTask, progressMap, DownloadStatus.EXECUTING, true)
            // 组内资源并行下载：常见场景是视频流和音频流两个文件。
            // coroutineScope 能保证任一子资源失败时，整个下载组一起进入失败/停止流程。
            coroutineScope {
                runningTask.requests.mapIndexed { index, request ->
                    async(Dispatchers.IO) {
                        downloadSingleResource(runningTask, request, progressMap.getValue(index))
                    }
                }.awaitAll()
            }
            // 下载成功后子资源已经移动到最终资源目录，缓存目录只保留 .part 临时文件。
            CommonLibs.requireDownloadCacheDir(task.id).deleteRecursively()
            publishSnapshot(runningTask, progressMap, DownloadStatus.COMPLETED, true)
        } catch (e: Exception) {
            val stopStatus = runningTask.requestedStopStatus
            if (stopStatus == DownloadStatus.STOPPED) {
                publishSnapshot(runningTask, progressMap, DownloadStatus.STOPPED, true)
            } else if (stopStatus == DownloadStatus.CANCELLED) {
                CommonLibs.requireDownloadCacheDir(task.id).deleteRecursively()
                publishSnapshot(runningTask, progressMap, DownloadStatus.CANCELLED, true)
            } else {
                val failure = (e as? ResourceDownloadException)?.failure
                    ?: DownloadFailure(DownloadFailure.Kind.UNKNOWN)
                if (e !is CancellationException) {
                    Log.e(TAG, "Task [G${task.id}] failed: kind=${failure.kind}, http=${failure.httpCode}")
                }
                publishSnapshot(runningTask, progressMap, DownloadStatus.FAILURE, true, failure)
            }
        } finally {
            mRunningTaskMap.remove(task.id)
            drainPendingTasks()
        }
    }

    /** 下载层完成所有恢复后才返回；已完成的子资源继续复用，避免音视频互相重下。 */
    private suspend fun downloadSingleResource(
        runningTask: RunningTask,
        request: ResourceRequest,
        progress: PartProgress
    ) {
        val outputFile = request.dashEntity.getOutputFile()
        if (outputFile.exists() && outputFile.length() > 0) {
            progress.downloaded.set(outputFile.length())
            progress.total.set(outputFile.length())
            return
        }
        val cacheFile = File(
            CommonLibs.requireDownloadCacheDir(runningTask.task.id),
            "stream-${request.dashEntity.taskId}-${request.dashEntity.dashId}-${request.dashEntity.codecId}.part"
        )
        // 重试只更新运行态快照；数据库仍处于下载中，队列槽位不会被重复登记。
        ResourceDownloader(NetworkManager.downloadClient, ::buildRequest).download(
            request.urls, cacheFile, outputFile,
            refreshUrls = { refreshResourceUrls(runningTask.task, request.dashEntity) },
            onProgress = { downloaded, total ->
                progress.downloaded.set(downloaded)
                progress.total.set(total)
                publishSnapshot(runningTask, null, DownloadStatus.EXECUTING, false)
            },
            onRetry = { retry ->
                progress.retry = retry
                if (retry != null) {
                    Log.d(TAG, "Task [G${runningTask.task.id}] retry=${retry.attempt}, " +
                        "kind=${retry.failure.kind}, http=${retry.failure.httpCode}, " +
                        "offset=${progress.downloaded.get()}")
                }
                publishSnapshot(runningTask, null, DownloadStatus.EXECUTING, true)
            }
        )
    }

    /** 创建下载和探测共用的请求，Range 与 If-Range 由传输层按检查点设置。 */
    private fun buildRequest(url: String): Request = Request.Builder()
        .url(url)
        .apply {
            NetworkConfig.DOWNLOAD_HEADERS.forEach { (key, value) -> header(key, value) }
            AccountManager.cookiesLiveData.value?.let { header("Cookie", it) }
        }
        .build()

    /** 聚合资源进度和恢复信息；仅最终失败携带失败原因，不新增持久化状态。 */
    private fun publishSnapshot(
        runningTask: RunningTask,
        progressMap: Map<Int, PartProgress>?,
        status: DownloadStatus,
        force: Boolean,
        failure: DownloadFailure? = null
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - runningTask.lastProgressEventTime.get() < PROGRESS_INTERVAL_MS) return
        runningTask.lastProgressEventTime.set(now)

        val progressValues = progressMap?.values ?: runningTask.progressMap?.values ?: emptyList()
        val currentProgress = progressValues.sumOf { it.downloaded.get() }
        val totalValues = progressValues.map { it.total.get() }
        // 任一子资源长度未知时，整体长度也视为未知，详情页只显示已下载大小。
        val fileSize = if (totalValues.isNotEmpty() && totalValues.all { it >= 0 }) {
            totalValues.sum()
        } else {
            -1L
        }
        val percent = when {
            status == DownloadStatus.COMPLETED -> 100
            // 执行中最高只报 99%，让完成事件成为唯一进入 100% 的路径。
            fileSize > 0 -> min(99, ((currentProgress * 100) / fileSize).toInt())
            else -> 0
        }
        val snapshot = DownloadGroupSnapshot(
            id = runningTask.task.id,
            status = status,
            percent = percent,
            currentProgress = currentProgress,
            fileSize = fileSize,
            retry = if (status == DownloadStatus.EXECUTING) {
                progressValues.mapNotNull { it.retry }.maxByOrNull { it.attempt }
            } else null,
            failure = failure
        )
        mSnapshotMap[runningTask.task.id] = snapshot
        EventBus.getDefault().post(DownloadStatusChangeEvent(runningTask.task, snapshot, status))
    }

    private data class ResourceRequest(
        val urls: List<String>,
        val dashEntity: DownloadDashEntity
    )

    /**
     * 下载组运行态。
     *
     * Job 将暂停/取消传播到请求、读取、退避和刷新；requestedStopStatus 区分用户操作与网络失败。
     */
    private class RunningTask(
        val task: DownloadTaskEntity,
        val requests: List<ResourceRequest>
    ) {
        val lastProgressEventTime = AtomicLong(0L)
        @Volatile var job: Job? = null
        @Volatile var requestedStopStatus: DownloadStatus? = null
        @Volatile var progressMap: Map<Int, PartProgress>? = null
    }

    /**
     * 单个子资源的下载进度。
     *
     * total 为 -1 表示服务端没有返回可用长度，进度百分比此时不能准确计算。
     */
    private class PartProgress {
        val downloaded = AtomicLong(0L)
        val total = AtomicLong(-1L)
        @Volatile var retry: DownloadRetry? = null
    }
}
