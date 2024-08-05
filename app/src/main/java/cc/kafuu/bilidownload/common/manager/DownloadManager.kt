package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.util.Log
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.model.DownloadTaskStatus
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.model.event.DownloadRequestFailedEvent
import cc.kafuu.bilidownload.common.model.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
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

    init {
        Aria.download(this).register()
    }

    // 下载任务组ID与对应的下载任务记录映射
    private val mTaskEntityMap = hashMapOf<Long, DownloadTaskEntity>()

    // 正在下载的流地址与对应的记录映射
    private val mDashEntityMap = hashMapOf<String, DownloadDashEntity>()

    fun containsTaskGroup(groupId: Long) = mTaskEntityMap.contains(groupId)

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
    private suspend fun getDownloadTaskEntity(group: DownloadGroupTask): DownloadTaskEntity? {
        val entity = mTaskEntityMap[group.entity.id]
            ?: DownloadRepository.getDownloadTaskByGroupId(group.entity.id)
        if (entity == null && DownloadTaskStatus.fromCode(group.state) != DownloadTaskStatus.CANCELLED) {
            // 查找不到对应的下载记录，则取消此下载任务
            Aria.download(this).load(group.entity.id).cancel(true)
            Log.d(TAG, "Task [G${group.entity.id}]: entity cannot be found, task cancelled")
        }
        if (entity != null && !mTaskEntityMap.contains(group.entity.id)) {
            // 查找到记录，且此记录不存在缓存中，则重新缓存此记录
            mTaskEntityMap[group.entity.id] = entity
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
    fun onTaskStatusChange(group: DownloadGroupTask?) {
        if (group == null) return

        Log.d(
            TAG,
            "Task [D${group.entity.id}] download status change, status: ${
                DownloadTaskStatus.fromCode(group.state)
            }"
        )
        mCoroutineScope.launch {
            val entity = getDownloadTaskEntity(group) ?: return@launch
            val status = DownloadTaskStatus.fromCode(group.state)
            if (status.isEndStatus) {
                // 状态为终止态
                onTaskEnd(entity, group)
            }
            EventBus.getDefault().post(DownloadStatusChangeEvent(entity, group, status))
        }
    }

    @onSubTaskFail
    fun onSubTaskFail(group: DownloadGroupTask?, subEntity: DownloadEntity?) {
        if (group == null) return

        Log.d(TAG, "onSubTaskFail: $group, subEntity: $subEntity")
        mCoroutineScope.launch {
            val entity = getDownloadTaskEntity(group) ?: return@launch
            EventBus.getDefault().post(
                DownloadStatusChangeEvent(entity, group, DownloadTaskStatus.FAILURE)
            )
        }
    }

    /**
     * 下载状态为中止态时调用此函数
     * 如果下载成功则将下载缓存输出到对应的未知
     * 此函数还负责清理缓存 */
    private fun onTaskEnd(task: DownloadTaskEntity, group: DownloadGroupTask) {
        group.entity.subEntities.forEach {
            val taskEntity = mDashEntityMap[it.url] ?: return@forEach
            File(it.filePath).apply {
                if (group.isComplete) renameTo(taskEntity.getOutputFile())
            }
            mDashEntityMap.remove(it.url)
        }
        mTaskEntityMap.remove(group.entity.id)

        CommonLibs.requireDownloadCacheDir(task.id).let {
            it.deleteRecursively()
            it.delete()
        }
    }

    /**
     * 请求下载指定任务的视频资源。
     *
     * @param task 包含下载任务必要信息的`DownloadTaskEntity`对象。
     *
     * @note 此函数在内部通过异步回调处理网络响应，因此不会立即返回下载结果。
     *
     */
    suspend fun requestDownload(task: DownloadTaskEntity) {
        Log.d(TAG, "Task [T${task.id}] request download")
        if (task.groupId != null && !containsTaskGroup(task.groupId!!)) {
            // 此记录已经被分配任务ID且未被任务下载管理器识别
            // 此任务可能是下载过程中应用程序退出中断或者下载失败的任务被用户点击重新下载
            // 这两种情况都需要重新为任务分配一个新的id并重启下载
            Aria.download(this).loadGroup(task.groupId!!).let {
                DownloadRepository.update(task.apply { groupId = null })
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

    /**
     * 当获取播放流信息完成时调用此函数。
     *
     * 该函数负责处理从BiliPlayStreamDash获取的播放流信息。它尝试根据返回的数据启动下载任务。
     * 如果在处理过程中遇到任何异常（例如，无法找到对应的视频或音频资源），则会通知所有注册的状态监听器请求失败。
     *
     * @param task 当前需要下载的任务实体，包含了下载所需的所有相关信息。
     * @param httpCode 服务器响应的HTTP状态码。
     * @param code 业务相关的状态码。
     * @param message 服务器响应的状态消息或错误消息。
     * @param data 从服务器获取的播放流数据，包含视频和音频资源的详细信息。
     *
     * @throws Exception 如果在获取下载资源的URL过程中遇到问题，例如找不到指定的视频或音频ID对应的资源，则会抛出异常。
     */
    fun onGetPlayStreamDashDone(
        task: DownloadTaskEntity,
        httpCode: Int,
        code: Int,
        message: String,
        data: BiliPlayStreamDash
    ) = mCoroutineScope.launch {
        try {
            val urls = getDownloadResourceUrls(task, data)
            if (urls.isNotEmpty()) {
                doStartDownload(task, urls)
            } else {
                throw IllegalStateException("Task [G${task.groupId}] no resources available for download")
            }
        } catch (e: Exception) {
            EventBus.getDefault().post(
                DownloadRequestFailedEvent(task, httpCode, code, e.message ?: "unknown error")
            )
        }
    }

    /**
     * 获取下载资源的URL列表。
     *
     * 此函数用于从给定的BiliPlayStreamDash对象中检索视频和音频流的URL。
     * 它会根据DownloadTaskEntity中指定的dashVideoId和dashAudioId来查找对应的视频和音频资源。
     *
     * @param task DownloadTaskEntity对象，包含需要下载的视频和音频的ID信息。
     * @param dash BiliPlayStreamDash对象，包含视频和音频流的详细信息。
     * @return 包含视频和音频流URL的列表。如果找到对应的视频和音频资源
     */
    private suspend fun getDownloadResourceUrls(
        task: DownloadTaskEntity,
        dash: BiliPlayStreamDash
    ): List<String> {
        val resources = dash.video + dash.getAllAudio()
        return DownloadRepository.queryDashList(task).mapNotNull { dashEntity ->
            resources.find {
                it.id == dashEntity.dashId && it.codecId == dashEntity.codecId
            }?.getStreamUrl()?.also {
                mDashEntityMap[it] = dashEntity
            }
        }
    }

    @Synchronized
    private fun doStartDownload(task: DownloadTaskEntity, resourceUrls: List<String>) {
        val groupId = Aria.download(this)
            .loadGroup(resourceUrls)
            .option(HttpOption().apply {
                NetworkConfig.DOWNLOAD_HEADERS.forEach { (key, value) -> addHeader(key, value) }
                AccountManager.cookiesLiveData.value?.let { addHeader("Cookie", it) }
            })
            .setDirPath(CommonLibs.requireDownloadCacheDir(task.id).path)
            .ignoreCheckPermissions()
            .unknownSize()
            .create()

        task.groupId = groupId
        task.status = DownloadTaskEntity.STATE_DOWNLOADING

        mTaskEntityMap[groupId] = task
        mCoroutineScope.launch { DownloadRepository.update(task) }

        Log.d(TAG, "Task [G${task.groupId}] start download")
    }
}