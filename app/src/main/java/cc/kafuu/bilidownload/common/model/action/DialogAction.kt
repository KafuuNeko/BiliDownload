package cc.kafuu.bilidownload.common.model.action

import cc.kafuu.bilidownload.common.core.dialog.CoreBasicsDialog
import java.io.Serializable

class DialogAction(
    val dialog: CoreBasicsDialog<*, *>,
    val failed: ((exception: Throwable) -> Unit)? = null,
    val success: ((result: Serializable) -> Unit)? = null,
) : ViewAction()