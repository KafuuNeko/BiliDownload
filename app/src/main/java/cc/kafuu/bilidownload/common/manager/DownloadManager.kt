package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.util.Log
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.model.event.DownloadRequestFailedEvent
import cc.kafuu.bilidownload.common.model.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.service.DownloadService
import com.arialyy.annotations.DownloadGroup
import com.arialyy.annotations.DownloadGroup.onSubTaskFail
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.common.HttpOption
import com.arialyy.aria.core.download.DownloadEntity
import com.arialyy.aria.core.task.DownloadGroupTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.io.File


object DownloadManager {

    private const val TAG = "DownloadManager"

    private val mCoroutineScope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

    enum class TaskStatus(val code: Int, val isEndStatus: Boolean) {
        FAILURE(0, true),
        COMPLETED(1, true),
        STOPPED(2, false),
        WAITING(3, false),
        EXECUTING(4, false),
        PREPROCESSING(5, false),
        PREPROCESSING_COMPLETED(6, false),
        CANCELLED(7, true);

        companion object {
            fun fromCode(code: Int): TaskStatus = entries.find { it.code == code }
                ?: throw IllegalArgumentException("Invalid code")
        }
    }

    init {
        Aria.download(this).register()
    }

    private val mTaskEntityMap = hashMapOf<Long, DownloadTaskEntity>()
    private val mDashEntityMap = hashMapOf<String, DownloadDashEntity>()
    private val mDownloadTaskDao by lazy { CommonLibs.requireAppDatabase().downloadTaskDao() }

    fun containsTask(downloadTaskId: Long) = mTaskEntityMap.contains(downloadTaskId)

    suspend fun startDownload(
        context: Context,
        bvid: String,
        cid: Long,
        resources: List<BiliDashModel>
    ) = DownloadRepository.createNewRecord(bvid, cid, resources).also {
        DownloadService.startDownload(context, it)
    }

    /**
     * 通过下载任务ID查找下载记录
     * 优先在缓存Map中查找，若是未查找到则查找数据库
     * */
    private suspend fun getDownloadTaskEntity(task: DownloadGroupTask): DownloadTaskEntity? {
        val entity = mTaskEntityMap[task.entity.id]
            ?: mDownloadTaskDao.getDownloadTaskByDownloadTaskId(task.entity.id)
        if (entity == null && TaskStatus.fromCode(task.state) != TaskStatus.CANCELLED) {
            // 查找不到对应的下载记录，则取消此下载任务
            Aria.download(this).load(task.entity.id).cancel(true)
            Log.d(TAG, "Task [D${task.entity.id}]: entity cannot be found, task cancelled")
        }
        if (entity != null && !mTaskEntityMap.contains(task.entity.id)) {
            // 查找到记录，且此记录不存在缓存中，则重新缓存此记录
            mTaskEntityMap[task.entity.id] = entity
        }
        return entity
    }

    @Synchronized
    @DownloadGroup.onTaskComplete
    @DownloadGroup.onTaskCancel
    @DownloadGroup.onTaskFail
    @DownloadGroup.onTaskRunning
    @DownloadGroup.onTaskStop
    @DownloadGroup.onTaskStart
    fun onTaskStatusChange(task: DownloadGroupTask?) {
        if (task == null) return

        Log.d(
            TAG,
            "Task [D${task.entity.id}] download status change, status: ${TaskStatus.fromCode(task.state)}"
        )
        mCoroutineScope.launch {
            val entity = getDownloadTaskEntity(task) ?: return@launch
            val status = TaskStatus.fromCode(task.state)
            if (status.isEndStatus) {
                // 状态为终止态
                onTaskEnd(entity, task)
            }
            EventBus.getDefault().post(DownloadStatusChangeEvent(entity, task, status))
        }
    }

    @onSubTaskFail
    fun onSubTaskFail(groupTask: DownloadGroupTask?, subEntity: DownloadEntity?) {
        if (groupTask == null) return

        Log.d(TAG, "onSubTaskFail: $groupTask, subEntity: $subEntity")
        mCoroutineScope.launch {
            val entity = getDownloadTaskEntity(groupTask) ?: return@launch
            EventBus.getDefault().post(
                DownloadStatusChangeEvent(entity, groupTask, TaskStatus.FAILURE)
            )
        }
    }

    /**
     * 下载状态为中止态时调用此函数
     * 如果下载成功则将下载缓存输出到对应的未知
     * 此函数还负责清理缓存 */
    private fun onTaskEnd(entity: DownloadTaskEntity, task: DownloadGroupTask) {
        task.entity.subEntities.forEach {
            val entity = mDashEntityMap[it.url] ?: return@forEach
            File(it.filePath).apply {
                if (task.isComplete) renameTo(entity.getOutputFile())
            }
            mDashEntityMap.remove(it.url)
        }
        mTaskEntityMap.remove(task.entity.id)

        CommonLibs.requireDownloadCacheDir(entity.id).let {
            it.deleteRecursively()
            it.deleteOnExit()
        }
    }

    /**
     * 请求下载指定任务的视频资源。
     *
     * @param entity 包含下载任务必要信息的`DownloadTaskEntity`对象。
     *
     * @note 此函数在内部通过异步回调处理网络响应，因此不会立即返回下载结果。
     *
     */
    suspend fun requestDownload(entity: DownloadTaskEntity) {
        Log.d(TAG, "Task [E${entity.id}] request download")

        if (entity.downloadTaskId != null &&
            !containsTask(entity.downloadTaskId!!) &&
            entity.status == DownloadTaskEntity.STATUS_DOWNLOADING
        ) {
            // 此记录存在一个未被下载管理器缓存的下载记录，且状态为正在下载
            // 判定为任务未下载过程中服务被中止，重新启动下载
            Aria.download(this).loadGroup(entity.downloadTaskId!!).let {
                mDownloadTaskDao.update(entity.apply { downloadTaskId = null })
                it.ignoreCheckPermissions().cancel(true)
            }
        }
        object : IServerCallback<BiliPlayStreamDash> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliPlayStreamDash
            ) {
                onGetPlayStreamDashDone(entity, httpCode, code, message, data)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                Log.e(TAG, "onFailure: httpCode = $httpCode, code = $code, message = $message")
                EventBus.getDefault().post(
                    DownloadRequestFailedEvent(entity, httpCode, code, message)
                )
            }
        }.apply {
            NetworkManager.biliVideoRepository.requestPlayStreamDash(
                entity.biliBvid,
                entity.biliCid,
                this
            )
        }
    }

    /**
     * 当获取播放流信息完成时调用此函数。
     *
     * 该函数负责处理从BiliPlayStreamDash获取的播放流信息。它尝试根据返回的数据启动下载任务。
     * 如果在处理过程中遇到任何异常（例如，无法找到对应的视频或音频资源），则会通知所有注册的状态监听器请求失败。
     *
     * @param entity 当前需要下载的任务实体，包含了下载所需的所有相关信息。
     * @param httpCode 服务器响应的HTTP状态码。
     * @param code 业务相关的状态码。
     * @param message 服务器响应的状态消息或错误消息。
     * @param data 从服务器获取的播放流数据，包含视频和音频资源的详细信息。
     *
     * @throws Exception 如果在获取下载资源的URL过程中遇到问题，例如找不到指定的视频或音频ID对应的资源，则会抛出异常。
     */
    fun onGetPlayStreamDashDone(
        entity: DownloadTaskEntity,
        httpCode: Int,
        code: Int,
        message: String,
        data: BiliPlayStreamDash
    ) = mCoroutineScope.launch {
        try {
            val urls = getDownloadResourceUrls(entity, data)
            if (urls.isNotEmpty()) {
                doStartDownload(entity, urls)
            } else {
                throw IllegalStateException("Task [D${entity.downloadTaskId}] no resources available for download")
            }
        } catch (e: Exception) {
            EventBus.getDefault().post(
                DownloadRequestFailedEvent(entity, httpCode, code, e.message ?: "unknown error")
            )
        }
    }

    /**
     * 获取下载资源的URL列表。
     *
     * 此函数用于从给定的BiliPlayStreamDash对象中检索视频和音频流的URL。
     * 它会根据DownloadTaskEntity中指定的dashVideoId和dashAudioId来查找对应的视频和音频资源。
     *
     * @param entity DownloadTaskEntity对象，包含需要下载的视频和音频的ID信息。
     * @param dash BiliPlayStreamDash对象，包含视频和音频流的详细信息。
     * @return 包含视频和音频流URL的列表。如果找到对应的视频和音频资源
     */
    private suspend fun getDownloadResourceUrls(
        entity: DownloadTaskEntity,
        dash: BiliPlayStreamDash
    ): List<String> {
        val resources = dash.video + dash.getAllAudio()
        return DownloadRepository.queryDashList(entity).mapNotNull { dashEntity ->
            resources.find {
                it.id == dashEntity.dashId && it.codecId == dashEntity.codecId
            }?.getStreamUrl()?.also {
                mDashEntityMap[it] = dashEntity
            }
        }
    }

    @Synchronized
    private fun doStartDownload(entity: DownloadTaskEntity, resourceUrls: List<String>) {
        val downloadTaskId = Aria.download(this)
            .loadGroup(resourceUrls)
            .option(HttpOption().apply {
                NetworkConfig.DOWNLOAD_HEADERS.forEach { (key, value) -> addHeader(key, value) }
                AccountManager.cookiesLiveData.value?.let { addHeader("Cookie", it) }
            })
            .setDirPath(CommonLibs.requireDownloadCacheDir(entity.id).path)
            .ignoreCheckPermissions()
            .unknownSize()
            .create()

        entity.downloadTaskId = downloadTaskId
        entity.status = DownloadTaskEntity.STATUS_DOWNLOADING

        mTaskEntityMap[downloadTaskId] = entity
        mCoroutineScope.launch { mDownloadTaskDao.update(entity) }

        Log.d(TAG, "Task [D${entity.downloadTaskId}] start download")
    }
}