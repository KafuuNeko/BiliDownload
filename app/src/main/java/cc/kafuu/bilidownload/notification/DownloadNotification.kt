package cc.kafuu.bilidownload.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.service.DownloadService

class DownloadNotification(context: Context) : NotificationHelper(context) {
    override fun getChannelId() = "download_channel"
    override fun getChannelName() = CommonLibs.getString(R.string.notification_bvd_downloading)

    @SuppressLint("ForegroundServiceType")
    fun startForeground(service: DownloadService) {
        service.startForeground(
            getFixedNotificationId(getChannelId()), getNotificationBuild(
                R.drawable.ic_download,
                CommonLibs.getString(R.string.notification_title_download_foreground),
            ).build()
        )
    }

    fun showMessageNotification(title: CharSequence, message: CharSequence) {
        val notificationManager =
            mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            getNewNotificationId(), getNotificationBuild(
                R.drawable.ic_download, title, message
            ).build()
        )
    }
}