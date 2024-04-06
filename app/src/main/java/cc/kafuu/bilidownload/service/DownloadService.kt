package cc.kafuu.bilidownload.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.manager.IDownloadStatusListener
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.notification.DownloadNotification
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.task.DownloadGroupTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.properties.Delegates

class DownloadService : Service(), IDownloadStatusListener {
    companion object {
        private const val TAG = "DownloadService"

        fun startDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadService::class.java)
            intent.putExtra("taskId", taskId)
            context.startService(intent)
        }
    }

    private val mServiceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mDownloadTaskDao = CommonLibs.requireAppDatabase().downloadTaskDao()

    private var mRunningTaskCount by Delegates.observable(0) { _, _, value ->
        if (value > 0) return@observable
        stopSelf()
    }

    private lateinit var mDownloadManager: DownloadManager
    private lateinit var mDownloadNotification: DownloadNotification

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        Aria.download(this).register()

        mDownloadManager = DownloadManager()
        mDownloadNotification = DownloadNotification(this)

        mDownloadManager.register(this)
        mDownloadNotification.startForeground(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Aria.download(this).unRegister()
        mDownloadManager.unregister(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra("taskId", -1L)
        if (taskId == null || taskId == -1L) {
            return super.onStartCommand(intent, flags, startId)
        }
        Log.d(TAG, "onStartCommand: $taskId")
        mRunningTaskCount++
        mServiceScope.launch { doRequestDownload(taskId) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 开始请求下载资源 */
    private suspend fun doRequestDownload(taskId: Long) {
        mDownloadTaskDao.getDownloadTaskById(taskId)?.let {
            mDownloadManager.requestDownload(it)
        } ?: {
            Log.e(TAG, "Task [D$taskId] get download task entity failed")
            mRunningTaskCount--
        }
    }

    /**
     * 请求下载资源失败*/
    override fun onRequestFailed(
        entity: DownloadTaskEntity,
        httpCode: Int,
        code: Int,
        message: String
    ) {
        mRunningTaskCount--
    }

    /**
     * 开始下载资源 */
    override fun onStartDownload(entity: DownloadTaskEntity, downloadTaskId: Long) {
        Log.d(TAG, "Task [D$downloadTaskId] start download")
    }

    /**
     * 下载状态改变 */
    override fun onDownloadStatusChange(
        entity: DownloadTaskEntity,
        task: DownloadGroupTask,
        status: DownloadManager.Companion.TaskStatus
    ) {
        Log.d(TAG, "Task [D${task.entity.id}] status change, status: $status")
        // 是终止态
        if (status.isEndStatus) {
            mRunningTaskCount--
        }
    }


}