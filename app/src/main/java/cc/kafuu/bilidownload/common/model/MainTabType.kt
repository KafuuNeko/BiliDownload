package cc.kafuu.bilidownload.common.model

import androidx.annotation.IntDef


@IntDef(MainTabType.TAB_HOME, MainTabType.TAB_ME)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class MainTabType {
    companion object {
        const val TAB_HOME = 0
        const val TAB_ME = 1
    }
}
