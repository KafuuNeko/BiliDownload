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
        ActivityJumpAction(
            targetIntent,
            targetClass,
            finishCurrent
        ).also {
            sendViewAction(it)
        }
    }

    /**
     * 结束现有activity
     */
    @JvmOverloads
    open fun finishActivity(activityResult: ActivityResult? = null) {
        ActivityJumpAction(
            null,
            null,
            true,
            activityResult
        ).also {
            sendViewAction(it)
        }
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
        failed: (suspend (exception: Throwable) -> Unit)? = null,
        success: (suspend (result: Serializable) -> Unit)? = null
    ) {
        sendViewAction(DialogAction(dialog, failed, success))
    }
}