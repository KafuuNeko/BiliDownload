package cc.kafuu.bilidownload.common.model.action

import cc.kafuu.bilidownload.common.core.viewbinding.dialog.CoreBasicsDialog
import java.io.Serializable

class DialogAction(
    val dialog: CoreBasicsDialog<*, *>,
    val failed: (suspend (exception: Throwable) -> Unit)? = null,
    val success: (suspend (result: Serializable) -> Unit)? = null,
) : ViewAction()