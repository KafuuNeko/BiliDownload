package cc.kafuu.bilidownload.common.worker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import cc.kafuu.bilidownload.service.DownloadService

class DownloadWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val intent = Intent(applicationContext, DownloadService::class.java)
        ContextCompat.startForegroundService(applicationContext, intent)
        return Result.success()
    }
}