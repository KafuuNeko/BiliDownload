package cc.kafuu.bilidownload.notification

import android.app.Notification
import android.content.Context
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs

class DownloadNotification(context: Context) : NotificationHelper(context) {

    private val mNotificationId = mutableMapOf<Long, Int>()


    override fun getChannelId() = "download_channel"
    override fun getChannelName() = CommonLibs.getString(R.string.notification_bvd_downloading)

    fun getChannelNotificationId(): Int = getFixedNotificationId(getChannelId())

    fun getForegroundNotification(): Notification = getNotificationBuild(
        R.drawable.ic_download,
        CommonLibs.getString(R.string.notification_title_download_foreground),
    ).build()

    private fun showEntityMessageNotification(
        entity: DownloadTaskEntity,
        title: CharSequence,
        message: CharSequence?
    ) {
        val id = getNewNotificationId()
        getNotificationBuild(
            R.drawable.ic_download, title, message
        ).apply {
            setAutoCancel(true)
            setOngoing(false)
            mNotificationManager.notify(id, build())
        }
    }

    private fun showEntityProgressMessageNotification(
        entity: DownloadTaskEntity,
        title: CharSequence,
        percent: Int?
    ) {
        val id = mNotificationId.getOrPut(entity.id) { getNewNotificationId() }

        if (percent == null) {
            mNotificationManager.cancel(id)
            mNotificationId.remove(entity.id)
            return
        }

        getNotificationBuild(
            R.drawable.ic_download, title, null
        ).apply {
            setAutoCancel(false)
            setOngoing(true)
            setProgress(100, percent, false)
            mNotificationManager.notify(id, build())
        }
    }

    fun updateDownloadProgress(entity: DownloadTaskEntity, percent: Int?) {
        showEntityProgressMessageNotification(
            entity,
            CommonLibs.getString(R.string.notification_downloading_title)
                .format("${entity.biliBvid}(${entity.id})"),
            percent
        )
    }

    fun notificationDownloadCancel(entity: DownloadTaskEntity) {
        showEntityMessageNotification(
            entity,
            CommonLibs.getString(R.string.notification_cancelled_download_title),
            CommonLibs.getString(R.string.notification_cancelled_download_message)
                .format("${entity.biliBvid}(${entity.id})")
        )
    }

    fun notificationSynthesisFailed(entity: DownloadTaskEntity) {
        showEntityMessageNotification(
            entity,
            CommonLibs.getString(R.string.notification_synthesis_failed_title),
            CommonLibs.getString(R.string.notification_synthesis_failed_message)
                .format("${entity.biliBvid}(${entity.id})")
        )
    }

    fun notificationDownloadFailed(entity: DownloadTaskEntity) {
        showEntityMessageNotification(
            entity,
            CommonLibs.getString(R.string.notification_download_failed_title),
            CommonLibs.getString(R.string.notification_download_failed_message)
                .format("${entity.biliBvid}(${entity.id})")
        )
    }

    fun notificationRequestFailed(
        entity: DownloadTaskEntity,
        httpCode: Int,
        code: Int,
        message: String
    ) {
        showEntityMessageNotification(
            entity,
            CommonLibs.getString(R.string.notification_request_failed_title),
            CommonLibs.getString(R.string.notification_request_failed_message)
                .format("${entity.biliBvid}(${entity.id})", httpCode, code, message),
        )
    }
}