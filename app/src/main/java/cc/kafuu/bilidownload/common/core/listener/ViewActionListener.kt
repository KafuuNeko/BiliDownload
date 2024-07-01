package cc.kafuu.bilidownload.common.core.listener

import cc.kafuu.bilidownload.common.manager.PopMessageManager
import cc.kafuu.bilidownload.common.model.action.ActivityJumpAction
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.action.popmessage.PopMessageAction

class ViewActionListener(
    private val availableActivity: IAvailableActivity
) {
    private var mActivityJumpListener: ActivityJumpListener =
        ActivityJumpListener(availableActivity)

    fun onViewAction(action: ViewAction) {
        when (action) {
            // Activity跳转，转移到ActivityJumpListener处理
            is ActivityJumpAction -> mActivityJumpListener.onActivityJumpLiveDataChange(action)
            // 消息弹窗事件，转移给PopMessageManager处理
            is PopMessageAction -> PopMessageManager.popMessage(
                availableActivity.requireAvailableActivity(),
                action
            )
        }
    }
}