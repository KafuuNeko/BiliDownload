package cc.kafuu.bilidownload.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.notification.DownloadNotification
import com.arialyy.aria.core.Aria
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DownloadService : Service() {
    companion object {
        private const val TAG = "DownloadService"

        fun startDownload(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadService::class.java)
            intent.putExtra("taskId", taskId)
            context.startService(intent)
        }
    }

    private lateinit var mDownloadManager: DownloadManager
    private lateinit var mDownloadNotification: DownloadNotification

    private val mNotificationIdMap = hashMapOf<Long, Int>()

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        Aria.download(this).register()

        mDownloadManager = DownloadManager(this)
        mDownloadNotification = DownloadNotification(this)

        mDownloadNotification.startForeground(this)
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
        runBlocking { requestDownload(taskId) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private suspend fun initListener() {
        mDownloadManager.taskNumber.drop(1).collect { value ->
            if (value <= 0) stopSelf()
        }
    }

    private suspend fun requestDownload(taskId: Long) {
        CommonLibs.requireAppDatabase().downloadTaskDao().getDownloadTaskById(taskId)?.let {
            mDownloadManager.requestDownload(it)
        } ?: Log.e(TAG, "get download task entity failed")
    }
}