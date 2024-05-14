package cc.kafuu.bilidownload.common.core

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.result.ActivityResult
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.manager.ActivityStackManager
import cc.kafuu.bilidownload.common.manager.PopMessageManager
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.model.ActivityJumpData
import cc.kafuu.bilidownload.common.model.popmessage.PopMessage

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
        initPopMessage()
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
     * 初始化用于监听pop消息的LiveData
     */
    private fun initPopMessage() {
        if (mViewModel.popMessageLiveData.hasObservers()) {
            return
        }
        mViewModel.popMessageLiveData.observe(this) {
            onPopMessage(it)
        }
    }

    /**
     * 弹出消息事件
     */
    protected fun onPopMessage(message: PopMessage) {
       PopMessageManager.popMessage(this, message)
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
    private fun onActivityJumpLiveDataChange(jumpData: cc.kafuu.bilidownload.common.model.ActivityJumpData) {
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

    /**
     * 设置状态栏色值
     *
     * @param color·
     */
    protected fun setNavigationStatusColor(@ColorRes color: Int) {
        val window = window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = CommonLibs.getColor(color)
        }
    }

    /**
     * 设置沉浸式状态栏
     * 为防止状态栏与标题重合
     * 设置页面标题控件需设置：android:fitsSystemWindows="true"
     */
    protected fun setImmersionStatusBar() {
        if (window == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            val insetsController = window.insetsController
            insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
                it.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )  // 设置状态栏文字为深色
            }
        } else {
            // 旧版本
            val decorView = window.decorView
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)  // 添加这一行来设置状态栏文字为深色
            window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
        }
    }

}