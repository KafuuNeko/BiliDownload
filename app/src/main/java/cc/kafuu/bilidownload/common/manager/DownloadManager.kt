package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.util.Log
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.download.DownloadGroupSnapshot
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.system.measureTimeMillis

/**
 * 一个数据库下载任务对应一个下载组，组内可以有一个或多个
 * DASH 资源。执行器负责请求真实流地址、把资源下载到缓存文件、聚合进度并通过 EventBus 通知
 * DownloadService 继续处理登记资源、合成音视频、通知用户等业务流程。
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private const val BUFFER_SIZE = 128 * 1024
    private const val PROGRESS_INTERVAL_MS = 500L
    private const val PROBE_TIMEOUT_MS = 3000L

    private val mCoroutineScope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

    // 运行中的任务组。key 使用 DownloadTaskEntity.id，避免再维护第三方库的任务 ID。
    private val mRunningTaskMap = ConcurrentHashMap<Long, RunningTask>()

    // 最近一次状态快照，供历史列表和详情页直接查询当前进度。
    private val mSnapshotMap = ConcurrentHashMap<Long, DownloadGroupSnapshot>()

    // 暂停状态需要跨一次任务取消保留下来，恢复下载时再清除。
    private val mPausedTaskSet = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    fun containsTaskGroup(groupId: Long) = mRunningTaskMap.containsKey(groupId)

    fun getSnapshot(groupId: Long) = mSnapshotMap[groupId]

    fun isStopped(groupId: Long) = mSnapshotMap[groupId]?.status == DownloadStatus.STOPPED ||
        mPausedTaskSet.contains(groupId)

    suspend fun startDownload(
        context: Context,
        bvid: String,
        cid: Long,
        resources: List<BiliDashModel>
    ) = DownloadRepository.createNewRecord(bvid, cid, resources).also {
        DownloadService.startDownload(context, it)
    }

    fun cancelDownload(groupId: Long) {
        mPausedTaskSet.remove(groupId)
        stopRunningTask(groupId, DownloadStatus.CANCELLED)
    }

    fun stopDownload(groupId: Long) {
        mPausedTaskSet.add(groupId)
        stopRunningTask(groupId, DownloadStatus.STOPPED)
    }

    private fun stopRunningTask(groupId: Long, status: DownloadStatus) {
        mRunningTaskMap[groupId]?.let {
            it.requestedStopStatus = status
            it.calls.forEach(Call::cancel)
            it.job?.cancel()
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

        // 下载前必须重新请求播放流，因为 B 站返回的真实资源 URL 可能过期。
        object : IServerCallback<BiliPlayStreamDash> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliPlayStreamDash
            ) {
                onGetPlayStreamDashDone(task, httpCode, code, message, data)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                Log.e(TAG, "onFailure: httpCode = $httpCode, code = $code, message = $message")
                EventBus.getDefault().post(
                    DownloadRequestFailedEvent(task, httpCode, code, message)
                )
            }
        }.apply {
            NetworkManager.biliVideoRepository.requestPlayStreamDash(
                task.biliBvid,
                task.biliCid,
                this
            )
        }
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
            EventBus.getDefault().post(
                DownloadRequestFailedEvent(task, httpCode, code, e.message ?: "unknown error")
            )
        }
    }

    private suspend fun getDownloadResourceRequests(
        task: DownloadTaskEntity,
        dash: BiliPlayStreamDash
    ): List<ResourceRequest> {
        val resources = (dash.video ?: emptyList()) + dash.getAllAudio()
        // 数据库里的 DownloadDashEntity 是用户选择的资源；接口返回的是可下载资源列表。
        // 两边用 dashId + codecId 对齐，保证视频、音频或单资源任务都能准确匹配。
        return coroutineScope {
            DownloadRepository.queryDashList(task).map { dashEntity ->
                async {
                    resources.find {
                        it.id == dashEntity.dashId && it.codecId == dashEntity.codecId
                    }?.selectStreamUrl()?.let { url ->
                        ResourceRequest(url, dashEntity)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun BiliPlayStreamResource.selectStreamUrl(): String {
        return when (AppModel.downloadSourceMode) {
            DownloadSourceMode.AUTO_PROBE -> selectFastestStreamUrl()
            DownloadSourceMode.CUSTOM_HOST -> selectCustomHostStreamUrl() ?: getStreamUrl()
            else -> getStreamUrl()
        }
    }

    private suspend fun BiliPlayStreamResource.selectFastestStreamUrl(): String {
        val candidates = getStreamUrls()
        if (candidates.size <= 1) return candidates.firstOrNull() ?: getStreamUrl()

        val probeResults = coroutineScope {
            candidates.mapIndexed { index, url ->
                async(Dispatchers.IO) {
                    probeStreamUrl(index, url)
                }
            }.awaitAll()
        }

        return probeResults
            .filter { it.isAvailable }
            .minWithOrNull(compareBy<ProbeResult> { it.elapsedMs }.thenBy { it.index })
            ?.url
            ?: getStreamUrl()
    }

    private suspend fun BiliPlayStreamResource.selectCustomHostStreamUrl(): String? {
        val host = normalizeCustomHost(AppModel.downloadSourceCustomHost) ?: return null
        val candidates = getStreamUrls()
            .mapNotNull { it.replaceHost(host) }
            .distinct()
        if (candidates.isEmpty()) return null

        val probeResults = coroutineScope {
            candidates.mapIndexed { index, url ->
                async(Dispatchers.IO) {
                    probeStreamUrl(index, url)
                }
            }.awaitAll()
        }

        return probeResults
            .filter { it.isAvailable }
            .minWithOrNull(compareBy<ProbeResult> { it.elapsedMs }.thenBy { it.index })
            ?.url
            .also {
                if (it == null) {
                    Log.d(TAG, "Custom download source host is unavailable: $host")
                }
            }
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

    private fun probeStreamUrl(index: Int, url: String): ProbeResult {
        val request = buildProbeRequest(url)
        val call = NetworkManager.okHttpClient.newCall(request)
        call.timeout().timeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        var isAvailable = false
        val elapsedMs = measureTimeMillis {
            try {
                call.execute().use { response ->
                    isAvailable = response.code() == 200 || response.code() == 206
                    if (isAvailable) {
                        response.body()?.byteStream()?.read()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Probe stream url failed: ${e.message}")
            }
        }

        return ProbeResult(index, url, elapsedMs, isAvailable)
    }

    private fun buildProbeRequest(url: String): Request {
        return buildRequest(url, resumeBytes = 0L, rangeHeader = "bytes=0-0")
    }

    @Synchronized
    private fun doStartDownload(task: DownloadTaskEntity, requests: List<ResourceRequest>) {
        val groupId = task.id
        if (mRunningTaskMap.containsKey(groupId)) return

        // groupId 保持写回数据库，旧 UI 和恢复流程仍可通过它定位下载组。
        task.groupId = groupId
        task.status = TaskStatus.DOWNLOADING.code

        val runningTask = RunningTask(task, requests)
        mRunningTaskMap[groupId] = runningTask
        runningTask.job = mCoroutineScope.launch {
            DownloadRepository.update(task)
            runDownloadGroup(runningTask)
        }

        Log.d(TAG, "Task [G${task.groupId}] start download")
    }

    private suspend fun runDownloadGroup(runningTask: RunningTask) {
        val task = runningTask.task
        val progressMap = runningTask.requests.mapIndexed { index, _ ->
            index to PartProgress()
        }.toMap()
        runningTask.progressMap = progressMap

        publishSnapshot(runningTask, progressMap, DownloadStatus.EXECUTING, true)

        try {
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
            mRunningTaskMap.remove(task.id)
        } catch (e: Exception) {
            val stopStatus = runningTask.requestedStopStatus
            if (stopStatus == DownloadStatus.STOPPED) {
                publishSnapshot(runningTask, progressMap, DownloadStatus.STOPPED, true)
            } else if (stopStatus == DownloadStatus.CANCELLED) {
                CommonLibs.requireDownloadCacheDir(task.id).deleteRecursively()
                publishSnapshot(runningTask, progressMap, DownloadStatus.CANCELLED, true)
            } else {
                if (e !is CancellationException) {
                    Log.e(TAG, "Task [G${task.id}] download failed", e)
                }
                publishSnapshot(runningTask, progressMap, DownloadStatus.FAILURE, true)
            }
            mRunningTaskMap.remove(task.id)
        }
    }

    private suspend fun downloadSingleResource(
        runningTask: RunningTask,
        request: ResourceRequest,
        progress: PartProgress
    ) {
        val outputFile = request.dashEntity.getOutputFile()
        // 恢复或重试时，如果目标文件已经存在，视为该子资源已完成。
        if (outputFile.exists() && outputFile.length() > 0) {
            progress.downloaded.set(outputFile.length())
            progress.total.set(outputFile.length())
            return
        }

        val cacheFile = File(
            CommonLibs.requireDownloadCacheDir(runningTask.task.id),
            "stream-${request.dashEntity.taskId}-${request.dashEntity.dashId}-${request.dashEntity.codecId}.part"
        )

        var resumeBytes = cacheFile.takeIf { it.exists() }?.length() ?: 0L
        repeat(2) { attempt ->
            ensureDownloadActive(runningTask)
            val httpRequest = buildRequest(request.url, resumeBytes)
            val call = NetworkManager.okHttpClient.newCall(httpRequest)
            runningTask.calls.add(call)
            try {
                call.execute().use { response ->
                    // 416 通常表示本地 .part 大于服务器当前资源尺寸；删除缓存后从头请求一次。
                    if (response.code() == 416 && resumeBytes > 0L && attempt == 0) {
                        cacheFile.delete()
                        resumeBytes = 0L
                        return@use
                    }
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response code ${response.code()}")
                    }

                    val body = response.body() ?: throw IOException("Empty response body")
                    val append = resumeBytes > 0L && response.code() == 206
                    // 如果服务端不支持 Range，会返回 200。此时不能追加旧缓存，只能覆盖重下。
                    if (resumeBytes > 0L && !append) {
                        cacheFile.delete()
                        resumeBytes = 0L
                    }

                    val startBytes = if (append) resumeBytes else 0L
                    progress.downloaded.set(startBytes)
                    body.contentLength().takeIf { it >= 0 }?.let {
                        progress.total.set(startBytes + it)
                    }

                    RandomAccessFile(cacheFile, "rw").use { file ->
                        if (!append) file.setLength(0L)
                        file.seek(startBytes)
                        val buffer = ByteArray(BUFFER_SIZE)
                        val input = body.byteStream()
                        while (true) {
                            ensureDownloadActive(runningTask)
                            val read = input.read(buffer)
                            if (read == -1) break
                            file.write(buffer, 0, read)
                            progress.downloaded.addAndGet(read.toLong())
                            // 高频 IO 回调会被 publishSnapshot 自己节流，避免列表刷新过密。
                            publishSnapshot(runningTask, null, DownloadStatus.EXECUTING, false)
                        }
                    }

                    val expectedSize = progress.total.get()
                    // contentLength 可用时做一次完整性校验，避免半截文件进入后续合成流程。
                    if (expectedSize >= 0 && cacheFile.length() < expectedSize) {
                        throw IOException("Downloaded file is incomplete")
                    }
                    moveFile(cacheFile, outputFile)
                    progress.downloaded.set(outputFile.length())
                    progress.total.set(outputFile.length())
                    return
                }
            } finally {
                runningTask.calls.remove(call)
            }
        }
        throw IOException("Unable to download resource")
    }

    private fun buildRequest(
        url: String,
        resumeBytes: Long,
        rangeHeader: String? = null
    ): Request {
        return Request.Builder()
            .url(url)
            .apply {
                // 复用项目原先下载请求头和登录 Cookie，保证下载接口鉴权行为不变。
                NetworkConfig.DOWNLOAD_HEADERS.forEach { (key, value) -> header(key, value) }
                AccountManager.cookiesLiveData.value?.let { header("Cookie", it) }
                if (rangeHeader != null) {
                    header("Range", rangeHeader)
                } else if (resumeBytes > 0L) {
                    header("Range", "bytes=$resumeBytes-")
                }
            }
            .build()
    }

    private fun moveFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        if (!source.renameTo(target)) {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            source.delete()
        }
    }

    private fun publishSnapshot(
        runningTask: RunningTask,
        progressMap: Map<Int, PartProgress>?,
        status: DownloadStatus,
        force: Boolean
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
            fileSize = fileSize
        )
        mSnapshotMap[runningTask.task.id] = snapshot
        EventBus.getDefault().post(DownloadStatusChangeEvent(runningTask.task, snapshot, status))
    }

    private fun RunningTask.ensureActive() {
        job?.ensureActive()
    }

    private suspend fun ensureDownloadActive(runningTask: RunningTask) {
        currentCoroutineContext().ensureActive()
        runningTask.ensureActive()
    }

    private data class ResourceRequest(
        val url: String,
        val dashEntity: DownloadDashEntity
    )

    private data class ProbeResult(
        val index: Int,
        val url: String,
        val elapsedMs: Long,
        val isAvailable: Boolean
    )

    /**
     * 下载组运行态。
     *
     * calls 用于把用户的取消/暂停操作传递到正在阻塞读取的 OkHttp 请求；requestedStopStatus
     * 用于区分主动暂停/取消和真正的网络失败。
     */
    private class RunningTask(
        val task: DownloadTaskEntity,
        val requests: List<ResourceRequest>
    ) {
        val calls = CopyOnWriteArraySet<Call>()
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
    }
}
