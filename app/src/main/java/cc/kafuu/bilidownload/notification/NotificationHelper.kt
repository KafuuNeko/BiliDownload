package cc.kafuu.bilidownload.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import cc.kafuu.bilidownload.common.model.AppModel

abstract class NotificationHelper(private val mContext: Context) {
    protected val mNotificationManager =
        mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    protected abstract fun getChannelId(): String
    protected abstract fun getChannelName(): CharSequence

    init {
        createNotificationChannel()
    }

    @Synchronized
    protected fun getNewNotificationId(): Int {
        var id = AppModel.latestNotificationId + 1
        if (id < 100) id = 100
        AppModel.latestNotificationId = id
        return id
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        mContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                getChannelId(),
                getChannelName(),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    protected fun getNotificationBuild(
        @DrawableRes smallIcon: Int,
        title: CharSequence?,
        content: CharSequence? = null,
        notificationIntent: PendingIntent? = null
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(mContext, getChannelId())
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(smallIcon)
            .setOnlyAlertOnce(true)
            .setContentIntent(notificationIntent)
    }

}
