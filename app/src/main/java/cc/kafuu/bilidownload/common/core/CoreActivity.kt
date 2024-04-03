package cc.kafuu.bilidownload.common.core

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import cc.kafuu.bilidownload.common.manager.ActivityStackManager


abstract class CoreActivity<V : ViewDataBinding, VM : CoreViewModel>(
    private val vmClass: Class<VM>,
    private val layoutId: Int,
    private val viewModelId: Int
) : AppCompatActivity() {
    protected lateinit var mViewDataBinding: V
    protected lateinit var mViewModel: VM

    protected abstract fun init()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityStackManager.pushActivity(this)
        mViewDataBinding = DataBindingUtil.setContentView(this, layoutId)
        mViewModel = ViewModelProvider(this)[vmClass]
        if (viewModelId != 0) {
            mViewDataBinding.setVariable(viewModelId, mViewModel)
        }
        mViewDataBinding.lifecycleOwner = this
        init()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            ActivityStackManager.removeActivity(this)
        }
    }
}