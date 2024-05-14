package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.widget.Toast
import cc.kafuu.bilidownload.common.model.popmessage.PopMessage
import cc.kafuu.bilidownload.common.model.popmessage.ToastMessage

object PopMessageManager {
    fun popMessage(context: Context, message: PopMessage) {
        when(message) {
            is ToastMessage -> doPopMessage(context, message)
        }
    }

    private fun doPopMessage(context: Context, message: ToastMessage) {
        Toast.makeText(context, message.content, message.duration).show()
    }
}