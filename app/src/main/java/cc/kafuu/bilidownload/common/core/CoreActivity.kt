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

/**
 * 本应用中所有Activity的基类，提供常用的数据绑定和视图模型设置功能。
 * 继承自 [AppCompatActivity]，并加入了对 [ViewDataBinding] 和 [CoreViewModel] 的支持，
 *
 * @param V 继承自 [ViewDataBinding] 的数据绑定类。
 * @param VM 继承自 [CoreViewModel] 的视图模型类。
 * @property vmClass 视图模型类的 Class 对象，用于实例化视图模型。
 * @property layoutId 布局资源ID，用于指定Activity使用的布局。
 * @property viewModelId 视图模型的变量ID，用于在布局中绑定视图模型。
 */
abstract class CoreActivity<V : ViewDataBinding, VM : CoreViewModel>(
    private val vmClass: Class<VM>,
    @LayoutRes private val layoutId: Int,
    private val viewModelId: Int
) : AppCompatActivity() {
    protected lateinit var mViewDataBinding: V
    protected lateinit var mViewModel: VM

    /**
     * 子类需要实现这个函数来初始化视图组件。
     */
    protected abstract fun initViews()

    /**
     * 完成数据绑定和视图模型的初始化工作，以及其他初始化操作。
     *
     * @param savedInstanceState savedInstanceState
     */
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

    /**
     * 如果Activity正在结束，则从Activity栈管理器中移除该Activity。
     */
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            ActivityStackManager.removeActivity(this)
        }
    }

    /**
     * 结束Activity
     *
     * @param activityResult 可选的 [ActivityResult] 对象，包含了返回结果的信息。
     */
    fun finishActivity(activityResult: ActivityResult? = null) {
        mViewModel.finishActivity(activityResult)
    }

    /**
     * 初始化用于监听Activity跳转的 LiveData。如果已经有观察者，则不进行重复的初始化。
     */
    private fun initActJumpData() {
        if (mViewModel.activityJumpLiveData.hasObservers()) {
            return
        }
        mViewModel.activityJumpLiveData.observe(this) {
            onActivityJumpLiveDataChange(it)
        }
    }

    /**
     * 当Activity跳转的 LiveData 发生变化时被调用，处理Activity跳转逻辑。
     *
     * @param jumpData Activity跳转的数据，包含了目标Activity和其他跳转信息。
     */
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