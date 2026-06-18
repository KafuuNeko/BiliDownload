package cc.kafuu.bilidownload.common.model

import com.chibatching.kotpref.KotprefModel

object AppModel : KotprefModel() {
    const val FIXED_NOTIFICATION_ID = 1

    var latestNotificationId by intPref(100000)

    private var downloadPathModeCode by intPref(
        default = DownloadPathMode.INTERNAL.code,
        key = "downloadPathMode"
    )

    private var downloadSourceModeCode by intPref(
        default = DownloadSourceMode.DEFAULT.code,
        key = "downloadSourceMode"
    )

    var downloadSourceCustomHost by stringPref("")

    var deleteSourceFilesAfterMerge by booleanPref(false)

    var downloadPathMode: DownloadPathMode
        get() = DownloadPathMode.fromCode(downloadPathModeCode)
        set(value) {
            downloadPathModeCode = value.code
        }

    var downloadSourceMode: DownloadSourceMode
        get() = DownloadSourceMode.fromCode(downloadSourceModeCode)
        set(value) {
            downloadSourceModeCode = value.code
        }
}
