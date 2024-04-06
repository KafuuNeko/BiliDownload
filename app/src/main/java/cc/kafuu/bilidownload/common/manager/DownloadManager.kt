package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.util.Log
import cc.kafuu.bilidownload.common.core.IServerCallback
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.utils.CommonLibs
import com.arialyy.annotations.DownloadGroup
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.common.HttpOption
import com.arialyy.aria.core.task.DownloadGroupTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class DownloadManager(context: Context) {
    companion object {
        private const val TAG = "DownloadManager"
    }

    init {
        Aria.init(context)
    }

    val taskNumber = MutableStateFlow(0)

    private val mEntityMap = mutableMapOf<Long, DownloadTaskEntity>()

    @DownloadGroup.onTaskComplete
    @DownloadGroup.onTaskCancel
    @DownloadGroup.onTaskFail
    fun handleTaskEnd(task: DownloadGroupTask) {
        taskNumber.value -= 1
        mEntityMap.remove(task.entity.id)
        Log.d(TAG, "task download end, task: $task")
    }

    @DownloadGroup.onTaskRunning
    fun handleSubTaskRunning(task: DownloadGroupTask) {
        mEntityMap[task.entity.id]?.let {
            Log.d(TAG, "downloading, task id:${task.entity.id}, percent: ${task.percent}")
        } ?: Log.e(TAG, "entity map could not find the task, task id: ${task.entity.id}")
    }

    @DownloadGroup.onTaskStop
    fun handleTaskStop(task: DownloadGroupTask) {
        Log.d(TAG, "task download stop, task: $task")
    }

    fun requestDownload(task: DownloadTaskEntity) {
        Log.d(TAG, "requestDownload: $task")
        NetworkManager.biliVideoRepository.getPlayStreamDash(
            task.biliBvid,
            task.biliCid,
            task.biliQn,
            object : IServerCallback<BiliPlayStreamDash> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: BiliPlayStreamDash
                ) {
                    startDownload(task, data)
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    Log.e(TAG, "onFailure: httpCode = $httpCode, code = $code, message = $message")
                }
            })
        taskNumber.value += 1
    }

    private fun startDownload(task: DownloadTaskEntity, dash: BiliPlayStreamDash) {
        Log.d(TAG, "startDownload: task:$task, dash:$dash")
        val videoDash = dash.video.find { it.id == task.dashVideoId }
            ?: throw IllegalArgumentException("Video(${task.dashVideoId}) not found")
        val audioDash = dash.audio.find { it.id == task.dashAudioId }
            ?: throw IllegalArgumentException("Audio(${task.dashAudioId}) not found")

        task.downloadTaskId = Aria.download(this)
            .loadGroup(listOf(videoDash.getStreamUrl(), audioDash.getStreamUrl()))
            .option(HttpOption().apply {
                NetworkConfig.DOWNLOAD_HEADERS.forEach { (key, value) -> addHeader(key, value) }
                NetworkConfig.biliCookies?.let { addHeader("Cookie", it) }
            })
            .setDirPath(CommonLibs.requireDownloadCacheDir("task-${task.id}").path)
            .unknownSize()
            .create()

        mEntityMap[task.downloadTaskId!!] = task

        Log.d(TAG, "startDownload: ${task.downloadTaskId}")

        runBlocking { CommonLibs.requireAppDatabase().downloadTaskDao().update(task) }
    }
}