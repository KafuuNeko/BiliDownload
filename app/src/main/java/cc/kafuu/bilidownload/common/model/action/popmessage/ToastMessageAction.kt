package cc.kafuu.bilidownload.common.model.action.popmessage

class ToastMessageAction(
    content: String,
    val duration: Int
): PopMessageAction(content)