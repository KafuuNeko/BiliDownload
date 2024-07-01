package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.widget.Toast
import cc.kafuu.bilidownload.common.model.action.popmessage.PopMessageAction
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction

object PopMessageManager {
    fun popMessage(context: Context, message: PopMessageAction) {
        when(message) {
            is ToastMessageAction -> doPopMessage(context, message)
        }
    }

    private fun doPopMessage(context: Context, message: ToastMessageAction) {
        Toast.makeText(context, message.content, message.duration).show()
    }
}