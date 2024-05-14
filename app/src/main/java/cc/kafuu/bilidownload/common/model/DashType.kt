package cc.kafuu.bilidownload.common.model

import androidx.annotation.IntDef
@IntDef(DashType.VIDEO, DashType.AUDIO)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class DashType {
    companion object {
        const val VIDEO = 0
        const val AUDIO = 1
    }
}
