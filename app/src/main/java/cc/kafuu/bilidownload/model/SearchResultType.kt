package cc.kafuu.bilidownload.model

import androidx.annotation.IntDef
@IntDef(SearchResultType.VIDEO_VIEW, SearchResultType.MEDIA_VIEW)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class SearchResultType {
    companion object {
        const val VIDEO_VIEW = 0
        const val MEDIA_VIEW = 1
    }
}
