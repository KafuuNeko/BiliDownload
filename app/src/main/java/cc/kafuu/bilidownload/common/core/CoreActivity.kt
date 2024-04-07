package cc.kafuu.bilidownload.common.core

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import cc.kafuu.bilidownload.common.manager.ActivityStackManager
import cc.kafuu.bilidownload.model.ActivityJumpData


abstract class CoreActivity<V : ViewDataBinding, VM : CoreViewModel>(
    private val vmClass: Class<VM>,
    @LayoutRes private val layoutId: Int,
    private val viewModelId: Int
) : AppCompatActivity() {
    protected lateinit var mViewDataBinding: V
    protected lateinit var mViewModel: VM

    protected abstract fun initViews()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityStackManager.pushActivity(this)
        mViewDataBinding = DataBindingUtil.setContentView(this, layoutId)
        mViewModel = ViewModelProvider(this)[vmClass]
        if (viewModelId != 0) {
            mViewDataBinding.setVariable(viewModelId, mViewModel)
        }
        mViewDataBinding.lifecycleOwner = this
        initActJumpData()
        initViews()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            ActivityStackManager.removeActivity(this)
        }
    }

    fun finishActivity(activityResult: ActivityResult? = null) {
        mViewModel.finishActivity(activityResult)
    }

    private fun initActJumpData() {
        if (mViewModel.activityJumpLiveData.hasObservers()) {
            return
        }
        mViewModel.activityJumpLiveData.observe(this) {
            onActivityJumpLiveDataChange(it)
        }
    }

    private fun onActivityJumpLiveDataChange(jumpData: ActivityJumpData) {
        if (jumpData.isDeprecated) {
            return
        }

        jumpData.targetClass?.let { targetClass ->
            (jumpData.targetIntent ?: Intent()).apply {
                component = ComponentName(this@CoreActivity, targetClass)
            }.also { targetIntent ->
                startActivity(targetIntent)
            }
        }

        if (jumpData.finishCurrent) {
            jumpData.activityResult?.let { activityResult ->
                setResult(activityResult.resultCode, activityResult.data)
            }
            finish()
        }

        jumpData.isDeprecated = true
    }
}