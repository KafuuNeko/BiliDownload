package cc.kafuu.bilidownload.common.constant

import androidx.annotation.IntDef
@IntDef(MediaSteamType.VIDEO, MediaSteamType.AUDIO)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class MediaSteamType {
    companion object {
        const val VIDEO = 0
        const val AUDIO = 1
    }
}
