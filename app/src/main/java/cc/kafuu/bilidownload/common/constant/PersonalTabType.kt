package cc.kafuu.bilidownload.common.constant

import androidx.annotation.IntDef


@IntDef(PersonalTabType.TAB_HISTORY, PersonalTabType.TAB_FAVORITE)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class PersonalTabType {
    companion object {
        const val TAB_HISTORY = 0
        const val TAB_FAVORITE = 1
    }
}
