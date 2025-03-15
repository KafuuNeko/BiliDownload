package cc.kafuu.bilidownload.notification

import android.app.Notification
import android.content.Context
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity

class DownloadNotification(context: Context) : NotificationHelper(context) {

    private val mNotificationId = mutableMapOf<Long, Int>()


    override fun getChannelId() = "download_channel"
    override fun getChannelName() = CommonLibs.getString(R.string.notification_bvd_downloading)

    fun getForegroundNotification(): Notification = getNotificationBuild(
        R.drawable.ic_downloading,
        CommonLibs.getString(R.string.notification_title_download_foreground),
    ).build()

    private fun showTaskMessageNotification(
        task: DownloadTaskEntity,
        title: CharSequence,
        message: CharSequence?
    ) {
        val id = getNewNotificationId()
        getNotificationBuild(
            R.drawable.ic_downloading, title, message
        ).apply {
            setAutoCancel(true)
            setOngoing(false)
            mNotificationManager.notify(id, build())
        }
    }

    private fun showTaskProgressMessageNotification(
        task: DownloadTaskEntity,
        title: CharSequence,
        percent: Int?
    ) {
        val id = mNotificationId.getOrPut(task.id) { getNewNotificationId() }

        if (percent == null) {
            mNotificationManager.cancel(id)
            mNotificationId.remove(task.id)
            return
        }

        getNotificationBuild(
            R.drawable.ic_downloading, title, null
        ).apply {
            setAutoCancel(false)
            setOngoing(true)
            setProgress(100, percent, false)
            mNotificationManager.notify(id, build())
        }
    }

    fun updateDownloadProgress(task: DownloadTaskEntity, percent: Int?) {
        showTaskProgressMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_downloading_title)
                .format("${task.biliBvid}(${task.id})"),
            percent
        )
    }

    fun notificationDownloadCancel(task: DownloadTaskEntity) {
        showTaskMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_cancelled_download_title),
            CommonLibs.getString(R.string.notification_cancelled_download_message)
                .format("${task.biliBvid}(${task.id})")
        )
    }

    fun notificationSynthesisFailed(task: DownloadTaskEntity) {
        showTaskMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_synthesis_failed_title),
            CommonLibs.getString(R.string.notification_synthesis_failed_message)
                .format("${task.biliBvid}(${task.id})")
        )
    }

    fun notificationDownloadFailed(task: DownloadTaskEntity) {
        showTaskMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_download_failed_title),
            CommonLibs.getString(R.string.notification_download_failed_message)
                .format("${task.biliBvid}(${task.id})")
        )
    }

    fun notificationRequestFailed(
        task: DownloadTaskEntity,
        httpCode: Int,
        code: Int,
        message: String
    ) {
        showTaskMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_request_failed_title),
            CommonLibs.getString(R.string.notification_request_failed_message)
                .format("${task.biliBvid}(${task.id})", httpCode, code, message),
        )
    }

    fun notificationGetVideoDetailsFailed(
        task: DownloadTaskEntity,
        responseCode: Int,
        returnCode: Int,
        message: String
    ) {
        showTaskMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_get_video_details_failed_title),
            CommonLibs.getString(R.string.notification_get_video_details_failed_message)
                .format(responseCode, returnCode, message)
        )
    }
}