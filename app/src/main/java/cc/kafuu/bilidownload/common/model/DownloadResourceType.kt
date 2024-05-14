package cc.kafuu.bilidownload.common.model

import androidx.annotation.IntDef

@IntDef(DownloadResourceType.VIDEO, DownloadResourceType.AUDIO, DownloadResourceType.MIXED)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class DownloadResourceType {
    companion object {
        const val VIDEO = 0
        const val AUDIO = 1
        const val MIXED = 2
    }
}
