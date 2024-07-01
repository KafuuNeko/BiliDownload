package cc.kafuu.bilidownload.common.constant

import androidx.annotation.IntDef


@IntDef(
    ConfirmDialogStatus.WAITING,
    ConfirmDialogStatus.CONFIRMING,
    ConfirmDialogStatus.CLOSED
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
