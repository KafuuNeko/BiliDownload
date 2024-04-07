package cc.kafuu.bilidownload.common.core

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import cc.kafuu.bilidownload.model.ActivityJumpData

open class CoreViewModel : ViewModel() {
    val activityJumpLiveData = MutableLiveData<ActivityJumpData>()
    /**
     * 启动 activity
     */
    @JvmOverloads
    protected fun startActivity(
        targetIntent: Intent? = null,
        targetClass: Class<*>,
        finishCurrent: Boolean = false
    ) {
        activityJumpLiveData.postValue(ActivityJumpData(targetIntent, targetClass, finishCurrent))
    }

    /**
     * 结束现有activity
     */
    @JvmOverloads
    fun finishActivity(activityResult: ActivityResult? = null) {
        activityJumpLiveData.postValue(ActivityJumpData(null, null, true, activityResult))
    }
}