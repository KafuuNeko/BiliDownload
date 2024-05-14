package cc.kafuu.bilidownload.common.model

import androidx.annotation.IntDef


@IntDef(
    cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.Companion.WAITING,
    cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.Companion.CONFIRMING,
    cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.Companion.CLOSED
)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class ConfirmDialogStatus {
    companion object {
        const val WAITING = 0
        const val CONFIRMING = 1
        const val CLOSED = 2
    }
}
