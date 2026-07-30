package cc.kafuu.bilidownload.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.HistoryDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.MainActivity

object NotificationNavigation {
    private const val ACTION_OPEN_DOWNLOADS =
        "cc.kafuu.bilidownload.action.OPEN_DOWNLOADS"
    private const val ACTION_OPEN_DOWNLOAD_TASK =
        "cc.kafuu.bilidownload.action.OPEN_DOWNLOAD_TASK"
    private const val REQUEST_CODE_DOWNLOADS = Int.MIN_VALUE

    private val pendingIntentFlags =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun createDownloadsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DOWNLOADS
            data = "bvd://notification/downloads".toUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_DOWNLOADS,
            intent,
            pendingIntentFlags
        )
    }

    fun createTaskPendingIntent(context: Context, taskId: Long): PendingIntent {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DOWNLOADS
            data = "bvd://notification/downloads".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val detailsIntent = HistoryDetailsActivity.buildIntent(taskId).apply {
            setClass(context, HistoryDetailsActivity::class.java)
            action = ACTION_OPEN_DOWNLOAD_TASK
            data = "bvd://notification/task/$taskId".toUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivities(
            context,
            taskId.toRequestCode(),
            arrayOf(mainIntent, detailsIntent),
            pendingIntentFlags
        )
    }

    private fun Long.toRequestCode(): Int = (this xor (this ushr Int.SIZE_BITS)).toInt()
}
