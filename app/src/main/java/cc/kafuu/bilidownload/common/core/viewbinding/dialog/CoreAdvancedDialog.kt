package cc.kafuu.bilidownload.common.core.viewbinding.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import cc.kafuu.bilidownload.common.core.viewbinding.CoreViewModel
import cc.kafuu.bilidownload.common.core.viewbinding.listener.IAvailableActivity
import cc.kafuu.bilidownload.common.core.viewbinding.listener.ViewActionListener
import cc.kafuu.bilidownload.common.model.action.ViewAction
import java.io.Serializable


abstract class CoreAdvancedDialog<V : ViewDataBinding, RS : Serializable, VM : CoreViewModel>(
    private val vmClass: Class<VM>,
    @LayoutRes val layoutId: Int,
    private val viewModelId: Int
) : CoreBasicsDialog<V, RS>(layoutId), IAvailableActivity {
    private lateinit var mViewActionListener: ViewActionListener

    protected lateinit var mViewModel: VM

    override fun requireAvailableActivity() = requireActivity()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mViewDataBinding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        mViewModel = ViewModelProvider(this)[vmClass]
        if (viewModelId != 0) {
            mViewDataBinding.setVariable(viewModelId, mViewModel)
        }
        mViewDataBinding.lifecycleOwner = this
        mViewActionListener = ViewActionListener(this, this)
        initViewAction()
        return mViewDataBinding.root
    }

    /**
     * 初始化用于监听视图Action的监听器
     * 目前负责处理Activity跳转和消息弹窗Action
     */
    private fun initViewAction() {
        if (mViewModel.viewActionLiveData.hasObservers()) {
            return
        }

        mViewModel.viewActionLiveData.observe(viewLifecycleOwner) {
            onViewAction(it)
        }
    }

    /**
     * 有新的ViewAction事件时触发此函数
     */
    protected open fun onViewAction(action: ViewAction) {
        mViewActionListener.onViewAction(action)
    }
}

