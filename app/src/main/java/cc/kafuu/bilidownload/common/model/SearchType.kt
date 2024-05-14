package cc.kafuu.bilidownload.common.model

import androidx.annotation.IntDef
@IntDef(SearchType.VIDEO, SearchType.MEDIA_BANGUMI, SearchType.MEDIA_FT)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class SearchType {
    companion object {
        const val VIDEO = 0
        const val MEDIA_BANGUMI = 1
        const val MEDIA_FT = 2
    }
}
