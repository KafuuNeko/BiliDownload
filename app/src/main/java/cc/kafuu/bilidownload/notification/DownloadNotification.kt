package cc.kafuu.bilidownload.notification

import android.app.Notification
import android.content.Context
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.download.DownloadFailure
import cc.kafuu.bilidownload.common.download.DownloadRetry
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.DownloadFeedbackText

class DownloadNotification(context: Context) : NotificationHelper(context) {

    private val mNotificationId = mutableMapOf<Long, Int>()


    override fun getChannelId() = "download_channel"
    override fun getChannelName() = CommonLibs.getString(R.string.notification_bvd_downloading)

    fun getForegroundNotification(): Notification = getNotificationBuild(
        R.drawable.ic_downloading,
        CommonLibs.getString(R.string.notification_title_download_foreground),
        notificationIntent = NotificationNavigation.createDownloadsPendingIntent(mContext)
    ).build()

    private fun showTaskMessageNotification(
        task: DownloadTaskEntity,
        title: CharSequence,
        message: CharSequence?
    ) {
        val id = getNewNotificationId()
        getNotificationBuild(
            R.drawable.ic_downloading,
            title,
            message,
            NotificationNavigation.createTaskPendingIntent(mContext, task.id)
        ).apply {
            setAutoCancel(true)
            setOngoing(false)
            mNotificationManager.notify(id, build())
        }
    }

    private fun showTaskProgressMessageNotification(
        task: DownloadTaskEntity,
        title: CharSequence,
        percent: Int?,
        message: String? = null
    ) {
        val id = mNotificationId.getOrPut(task.id) { getNewNotificationId() }

        if (percent == null) {
            mNotificationManager.cancel(id)
            mNotificationId.remove(task.id)
            return
        }

        getNotificationBuild(
            R.drawable.ic_downloading,
            title,
            message,
            NotificationNavigation.createTaskPendingIntent(mContext, task.id)
        ).apply {
            setAutoCancel(false)
            setOngoing(true)
            setProgress(100, percent, false)
            mNotificationManager.notify(id, build())
        }
    }

    /** 复用同一进度通知展示恢复状态，自动重试不会生成额外失败通知。 */
    fun updateDownloadProgress(task: DownloadTaskEntity, percent: Int?, retry: DownloadRetry? = null) {
        showTaskProgressMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_downloading_title)
                .format("${task.biliBvid}(${task.id})"),
            percent,
            retry?.let(DownloadFeedbackText::retry)
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

    fun notificationDownloadCompleted(task: DownloadTaskEntity) {
        showTaskMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_download_completed_title),
            CommonLibs.getString(R.string.notification_download_completed_message)
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

    /** 仅在恢复预算耗尽后展示脱敏的失败原因。 */
    fun notificationDownloadFailed(task: DownloadTaskEntity, failure: DownloadFailure? = null) {
        showTaskMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_download_failed_title),
            CommonLibs.getString(
                R.string.notification_download_failed_reason,
                "${task.biliBvid}(${task.id})",
                DownloadFeedbackText.failure(failure)
            )
        )
    }

    /** 显示公共媒体库发布失败通知，并附带可用于定位资源的失败摘要。 */
    fun notificationPublishFailed(task: DownloadTaskEntity, reason: String) {
        showTaskMessageNotification(
            task,
            CommonLibs.getString(R.string.notification_publish_failed_title),
            CommonLibs.getString(
                R.string.notification_publish_failed_message,
                "${task.biliBvid}(${task.id})",
                reason
            )
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
            CommonLibs.getString(
                R.string.notification_request_failed_message,
                "${task.biliBvid}(${task.id})",
                httpCode,
                code,
                message
            ),
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
            CommonLibs.getString(
                R.string.notification_get_video_details_failed_message,
                responseCode,
                returnCode,
                message
            )
        )
    }
}
