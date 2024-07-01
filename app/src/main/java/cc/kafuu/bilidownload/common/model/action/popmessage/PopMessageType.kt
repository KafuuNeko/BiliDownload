package cc.kafuu.bilidownload.common.model.action.popmessage

import androidx.annotation.IntDef
@IntDef(PopMessageType.TOAST)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class PopMessageType {
    companion object {
        const val TOAST = 0
    }
}
