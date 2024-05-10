package cc.kafuu.bilidownload.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat

abstract class NotificationHelper(protected val mContext: Context) {

    companion object {
        private const val NOTIFICATION_ID_KEY = "notification_id"
    }

    private val prefs: SharedPreferences =
        mContext.getSharedPreferences("notification_id", Context.MODE_PRIVATE)
    protected val mNotificationManager =
        mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    protected abstract fun getChannelId(): String
    protected abstract fun getChannelName(): CharSequence

    init {
        createNotificationChannel()
    }

    @Synchronized
    protected fun getNewNotificationId(): Int {
        val newId = prefs.getInt(NOTIFICATION_ID_KEY, 0) + 1
        prefs.edit().putInt(NOTIFICATION_ID_KEY, newId).apply()
        return newId
    }

    protected fun getFixedNotificationId(name: String): Int {
        var id = prefs.getInt(name, 0)
        if (id == 0) {
            id = getNewNotificationId()
            prefs.edit().putInt(name, id).apply()
        }
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
