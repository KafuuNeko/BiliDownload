package cc.kafuu.bilidownload.common.core.viewbinding.listener

import android.content.ComponentName
import android.content.Intent
import cc.kafuu.bilidownload.common.model.action.ActivityJumpAction

class ActivityJumpListener(
    private val availableActivity: IAvailableActivity
) {
    /**
     * 当Activity跳转的 LiveData 发生变化时被调用，处理Activity跳转逻辑。
     *
     * @param jumpData Activity跳转的数据，包含了目标Activity和其他跳转信息。
     */
    fun onActivityJumpLiveDataChange(jumpData: ActivityJumpAction) {
        if (jumpData.isDeprecated) {
            return
        }

        val activity = availableActivity.requireAvailableActivity()
        jumpData.targetClass?.let { targetClass ->
            (jumpData.targetIntent ?: Intent()).apply {
                component = ComponentName(activity, targetClass)
            }.also { targetIntent ->
                activity.startActivity(targetIntent)
            }
        }

        if (jumpData.finishCurrent) {
            jumpData.activityResult?.let { activityResult ->
                activity.setResult(activityResult.resultCode, activityResult.data)
            }
            activity.finish()
        }

        jumpData.isDeprecated = true
    }
}