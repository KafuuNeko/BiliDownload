package cc.kafuu.bilidownload.common.core

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import cc.kafuu.bilidownload.common.model.ActivityJumpData
import cc.kafuu.bilidownload.common.model.popmessage.PopMessage

open class CoreViewModel : ViewModel() {
    val activityJumpLiveData = MutableLiveData<cc.kafuu.bilidownload.common.model.ActivityJumpData>()
    val popMessageLiveData = MutableLiveData<PopMessage>()
    /**
     * 启动 activity
     */
    @JvmOverloads
    protected fun startActivity(
        targetClass: Class<*>,
        targetIntent: Intent? = null,
        finishCurrent: Boolean = false
    ) {
        activityJumpLiveData.postValue(
            cc.kafuu.bilidownload.common.model.ActivityJumpData(
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
        activityJumpLiveData.postValue(
            cc.kafuu.bilidownload.common.model.ActivityJumpData(
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
    fun popMessage(message: PopMessage) {
        popMessageLiveData.postValue(message)
    }
}