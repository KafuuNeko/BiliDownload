package cc.kafuu.bilidownload.common.constant

import androidx.annotation.IntDef
@IntDef(BiliArchiveViewType.VIDEO_VIEW, BiliArchiveViewType.MEDIA_VIEW)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class BiliArchiveViewType {
    companion object {
        const val VIDEO_VIEW = 0
        const val MEDIA_VIEW = 1
    }
}
