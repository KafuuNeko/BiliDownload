package cc.kafuu.bilidownload.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.IServerCallback
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.view.activity.MainActivity
import com.arialyy.annotations.DownloadGroup
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.common.HttpOption
import com.arialyy.aria.core.task.DownloadGroupTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DownloadService : Service() {
    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "cc.kafuu.bilidownload.DownloadService"

        fun startDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadService::class.java)
            intent.putExtra("taskId", taskId)
            context.startService(intent)
        }
    }

    private var mTaskNumber = MutableStateFlow(0)

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        Aria.download(this).register()

        createNotificationChannel()
        startForeground(1, createNotification())

        CoroutineScope(Dispatchers.Default).launch {
            initListener()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra("taskId", -1L)
        Log.d(TAG, "onStartCommand: $taskId")
        if (taskId == null || taskId == -1L) {
            return super.onStartCommand(intent, flags, startId)
        }
        runBlocking {
            CommonLibs.requireAppDatabase().downloadTaskDao().getDownloadTaskById(taskId)?.let {
                requestDownload(it)
            } ?: Log.e(TAG, "get download task entity failed")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Download Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Service")
            .setContentText("Download in progress...")
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .build()
    }


    private suspend fun initListener() {
        mTaskNumber.drop(1)
            .collect { value ->
            if (value <= 0) {
                stopSelf()
            }
        }
    }

    @DownloadGroup.onTaskComplete
    @DownloadGroup.onTaskCancel
    @DownloadGroup.onTaskFail
    fun handleTaskEnd(task: DownloadGroupTask) {
        mTaskNumber.value -= 1
        Log.d(TAG, "handleTaskEnd: $task")
    }

    @DownloadGroup.onTaskRunning
    fun handleSubTaskRunning(task: DownloadGroupTask) {
        Log.d(TAG, "handleSubTaskRunning: ${task.percent}")
    }

    private fun requestDownload(task: DownloadTaskEntity) {
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
        mTaskNumber.value += 1
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
                NetworkConfig.biliCookies?.let { addHeader("Cookie", it)  }
            })
            .setDirPath(CommonLibs.requireDownloadCacheDir("task-${task.id}").path)
            .unknownSize()
            .create()

        Log.d(TAG, "startDownload: ${task.downloadTaskId}")

        runBlocking { CommonLibs.requireAppDatabase().downloadTaskDao().update(task) }
    }
}