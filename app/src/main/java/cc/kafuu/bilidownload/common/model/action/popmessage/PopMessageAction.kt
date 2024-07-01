package cc.kafuu.bilidownload.common.model.action.popmessage

import cc.kafuu.bilidownload.common.model.action.ViewAction

abstract class PopMessageAction(
    val content: String
): ViewAction()
