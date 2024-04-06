package cc.kafuu.bilidownload.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamData
import cc.kafuu.bilidownload.common.utils.SerializationUtils.getSerializable
import com.arialyy.annotations.DownloadGroup
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.task.DownloadGroupTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DownloadService : Service() {
    companion object {
        private const val TAG = "DownloadService"
    }

    private var mTaskNumber = MutableStateFlow(0)

    override fun onCreate() {
        Aria.download(this).register()
        CoroutineScope(Dispatchers.Default).launch {
            mTaskNumber.collect { value ->
                if (value <= 0) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getSerializable("requestData", DownloadTaskEntity::class.java)?.let {
            requestDownload(it)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @DownloadGroup.onTaskComplete
    @DownloadGroup.onTaskCancel
    @DownloadGroup.onTaskFail
    fun handleTaskEnd(task: DownloadGroupTask) {
        mTaskNumber.value -= 1
    }

    private fun requestDownload(data: DownloadTaskEntity) {
//        val taskId = Aria.download(this).loadGroup(urls)
//            .setDirPath(CommonLibs.requireDownloadCacheDir().path).create()
//        mDownloadIdSet.add(taskId)
        mTaskNumber.value += 1

    }

    private fun startDownload(data: BiliPlayStreamData) {

    }
}