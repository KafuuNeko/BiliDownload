package cc.kafuu.bilidownload.common.model.action.popmessage

import android.widget.Toast

class ToastMessageAction(
    content: String,
    val duration: Int = Toast.LENGTH_SHORT
): PopMessageAction(content)