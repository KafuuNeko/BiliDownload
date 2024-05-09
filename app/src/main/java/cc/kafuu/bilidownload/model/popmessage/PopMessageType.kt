package cc.kafuu.bilidownload.model.popmessage

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
