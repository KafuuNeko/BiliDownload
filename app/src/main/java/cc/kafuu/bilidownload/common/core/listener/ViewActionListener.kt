package cc.kafuu.bilidownload.common.core.listener

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import cc.kafuu.bilidownload.common.manager.PopMessageManager
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.common.model.action.ActivityJumpAction
import cc.kafuu.bilidownload.common.model.action.DialogAction
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.action.popmessage.PopMessageAction
import kotlinx.coroutines.launch

class ViewActionListener(
    private val lifecycleOwner: LifecycleOwner,
    private val availableActivity: IAvailableActivity
) {
    companion object {
        private const val TAG = "ViewActionListener"
    }

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
            // 对话框事件
            is DialogAction -> doDialogAction(action)
            // 无法处理的Action
            else -> Log.e(TAG, "unknown view action: $action")
        }
    }

    /**
     * 处理对话框事件
     */
    private fun doDialogAction(action: DialogAction) = lifecycleOwner.lifecycleScope.launch {
        when (val result = action.dialog.showAndWaitResult(lifecycleOwner)) {
            is ResultWrapper.Success -> action.success?.invoke(result.value)
            is ResultWrapper.Error -> action.failed?.invoke(result.error)
        }
    }
}