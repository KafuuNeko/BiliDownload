package cc.kafuu.bilidownload.common.manager

import android.util.Log
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs
import com.arialyy.annotations.DownloadGroup
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.common.HttpOption
import com.arialyy.aria.core.task.DownloadGroupTask
import kotlinx.coroutines.runBlocking

object DownloadManager {

    private const val TAG = "DownloadManager"

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

    val mStatusListener: MutableList<IDownloadStatusListener> = mutableListOf()
    private val mEntityMap = hashMapOf<Long, DownloadTaskEntity>()

    fun containsTask(downloadTaskId: Long) = mEntityMap.contains(downloadTaskId)

    fun register(listener: IDownloadStatusListener) {
        if (!mStatusListener.contains(listener)) {
            mStatusListener.add(listener)
        }
    }

    fun unregister(listener: IDownloadStatusListener) {
        mStatusListener.remove(listener)
    }

    @Synchronized
    @DownloadGroup.onTaskComplete
    @DownloadGroup.onTaskCancel
    @DownloadGroup.onTaskFail
    @DownloadGroup.onTaskRunning
    @DownloadGroup.onTaskStop
    @DownloadGroup.onTaskStart
    fun onTaskStatusChange(task: DownloadGroupTask) {
        Log.d(
            TAG,
            "Task [D${task.entity.id}] download status change, status: ${TaskStatus.fromCode(task.state)}"
        )
        runBlocking {
            val entity =
                mEntityMap[task.entity.id] ?: CommonLibs.requireAppDatabase().downloadTaskDao()
                    .getDownloadTaskByDownloadTaskId(task.entity.id)
            if (entity == null) {
                if (TaskStatus.fromCode(task.state) != TaskStatus.CANCELLED) {
                    task.cancel()
                }
                return@runBlocking
            }
            val status = TaskStatus.fromCode(task.state)
            mStatusListener.forEach { it.onDownloadStatusChange(entity, task, status) }
            if (status.isEndStatus) {
                mEntityMap.remove(task.entity.id)
            }
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
    fun requestDownload(entity: DownloadTaskEntity) {
        Log.d(TAG, "Task [E${entity.id}] request download")

        if (entity.downloadTaskId != null &&
            !containsTask(entity.downloadTaskId!!) &&
            entity.status == DownloadTaskEntity.STATUS_DOWNLOADING
        ) {
            Aria.download(this).loadGroup(entity.downloadTaskId!!).ignoreCheckPermissions().resume()
            return
        }

        NetworkManager.biliVideoRepository.getPlayStreamDash(
            entity.biliBvid,
            entity.biliCid,
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
                    mStatusListener.forEach { it.onRequestFailed(entity, httpCode, code, message) }
                }
            }
        )
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
    ) {
        try {
            doStartDownload(entity, getDownloadResourceUrls(entity, data))
        } catch (e: Exception) {
            mStatusListener.forEach {
                it.onRequestFailed(entity, httpCode, code, e.message ?: "unknown error")
            }
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
     * @return 包含视频和音频流URL的列表。如果找到对应的视频和音频资源，此列表将包含两个元素；否则，将抛出IllegalArgumentException。
     * @throws IllegalArgumentException 如果无法在dash参数提供的视频或音频资源中找到与task参数指定的ID匹配的项，则抛出此异常。
     */
    private fun getDownloadResourceUrls(
        entity: DownloadTaskEntity,
        dash: BiliPlayStreamDash
    ): List<String> {
        val videoDash =
            dash.video.find { it.id == entity.dashVideoId && it.codecId == entity.dashVideoCodecId }
                ?: throw IllegalArgumentException("Video(${entity.dashVideoId}) not found")
        val audioDash =
            dash.audio.find { it.id == entity.dashAudioId && it.codecId == entity.dashAudioCodecId }
                ?: throw IllegalArgumentException("Audio(${entity.dashAudioId}) not found")
        return listOf(videoDash.getStreamUrl(), audioDash.getStreamUrl())
    }

    @Synchronized
    private fun doStartDownload(entity: DownloadTaskEntity, resourceUrls: List<String>) {
        Aria.download(this)
            .loadGroup(resourceUrls)
            .option(HttpOption().apply {
                NetworkConfig.DOWNLOAD_HEADERS.forEach { (key, value) -> addHeader(key, value) }
                BiliManager.cookies.value?.let { addHeader("Cookie", it) }
            })
            .setDirPath(CommonLibs.requireDownloadCacheDir(entity.id).path)
            .ignoreCheckPermissions()
            .unknownSize()
            .apply {
                entity.downloadTaskId = entity.id
                mEntityMap[entity.id] = entity
                runBlocking { CommonLibs.requireAppDatabase().downloadTaskDao().update(entity) }
                create()
            }
        Log.d(TAG, "Task [D${entity.downloadTaskId}] start download")
    }
}