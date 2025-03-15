package cc.kafuu.bilidownload.common.model

import com.chibatching.kotpref.KotprefModel

object AppModel : KotprefModel() {
    const val FIXED_NOTIFICATION_ID = 1

    var latestNotificationId by intPref(100000)
}