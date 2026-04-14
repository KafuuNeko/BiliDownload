package cc.kafuu.bilidownload.common.model

import com.chibatching.kotpref.KotprefModel

object AppModel : KotprefModel() {
    const val FIXED_NOTIFICATION_ID = 1

    const val DOWNLOAD_PATH_INTERNAL = 0
    const val DOWNLOAD_PATH_EXTERNAL = 1

    var latestNotificationId by intPref(100000)
    var downloadPathMode by intPref(DOWNLOAD_PATH_INTERNAL)
}