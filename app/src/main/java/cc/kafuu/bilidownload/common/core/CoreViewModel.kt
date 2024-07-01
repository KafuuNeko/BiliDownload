package cc.kafuu.bilidownload.common.core

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import cc.kafuu.bilidownload.common.model.action.ActivityJumpAction
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.action.popmessage.PopMessageAction

open class CoreViewModel : ViewModel() {
    val viewActionLiveData = MutableLiveData<ViewAction>()

    /**
     * 启动 activity
     */
    @JvmOverloads
    protected fun startActivity(
        targetClass: Class<*>,
        targetIntent: Intent? = null,
        finishCurrent: Boolean = false
    ) {
        viewActionLiveData.postValue(
            ActivityJumpAction(
                targetIntent,
                targetClass,
                finishCurrent
            )
        )
    }

    /**
     * 结束现有activity
     */
    @JvmOverloads
    fun finishActivity(activityResult: ActivityResult? = null) {
        viewActionLiveData.postValue(
            ActivityJumpAction(
                null,
                null,
                true,
                activityResult
            )
        )
    }

    /**
     * 弹出消息
     */
    fun popMessage(message: PopMessageAction) {
        viewActionLiveData.postValue(message)
    }
}