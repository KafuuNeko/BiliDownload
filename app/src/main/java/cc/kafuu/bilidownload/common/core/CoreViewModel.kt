package cc.kafuu.bilidownload.common.core

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import cc.kafuu.bilidownload.common.core.dialog.CoreBasicsDialog
import cc.kafuu.bilidownload.common.model.action.ActivityJumpAction
import cc.kafuu.bilidownload.common.model.action.DialogAction
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.action.popmessage.PopMessageAction
import java.io.Serializable

open class CoreViewModel : ViewModel() {
    val viewActionLiveData = MutableLiveData<ViewAction>()

    /**
     * 发送一个ViewAction
     */
    protected fun sendViewAction(action: ViewAction) {
        viewActionLiveData.postValue(action)
    }

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
        sendViewAction(
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
        sendViewAction(message)
    }

    /**
     * 弹出对话框
     */
    fun popDialog(
        dialog: CoreBasicsDialog<*, *>,
        failed: ((exception: Throwable) -> Unit)? = null,
        success: ((result: Serializable) -> Unit)? = null
    ) {
        sendViewAction(DialogAction(dialog, failed, success))
    }
}