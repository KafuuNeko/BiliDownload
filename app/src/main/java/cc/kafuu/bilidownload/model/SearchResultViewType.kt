package cc.kafuu.bilidownload.model

import androidx.annotation.IntDef
@IntDef(SearchResultViewType.VIDEO_VIEW, SearchResultViewType.MEDIA_VIEW)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class SearchResultViewType {
    companion object {
        const val VIDEO_VIEW = 0
        const val MEDIA_VIEW = 1
    }
}
