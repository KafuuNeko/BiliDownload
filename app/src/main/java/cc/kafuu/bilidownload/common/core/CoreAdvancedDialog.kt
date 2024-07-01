package cc.kafuu.bilidownload.common.core

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import cc.kafuu.bilidownload.common.manager.PopMessageManager
import cc.kafuu.bilidownload.common.model.popmessage.PopMessage


abstract class CoreAdvancedDialog<V : ViewDataBinding, VM : CoreViewModel>(
    private val vmClass: Class<VM>,
    @LayoutRes val layoutId: Int,
    private val viewModelId: Int
) : CoreBasicsDialog<V>(layoutId), IAvailableActivity {
    private lateinit var mActivityJumpListener: ActivityJumpListener

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
        mActivityJumpListener = ActivityJumpListener(this)
        initPopMessage()
        initActJumpData()
        return mViewDataBinding.root
    }

    /**
     * 初始化用于监听pop消息的LiveData
     */
    private fun initPopMessage() {
        if (mViewModel.popMessageLiveData.hasObservers()) {
            return
        }
        mViewModel.popMessageLiveData.observe(viewLifecycleOwner) {
            onPopMessage(it)
        }
    }

    /**
     * 弹出消息事件
     */
    protected fun onPopMessage(message: PopMessage) {
        PopMessageManager.popMessage(requireContext(), message)
    }

    /**
     * 初始化用于监听Fragment跳转的 LiveData。如果已经有观察者，则不进行重复的初始化。
     */
    private fun initActJumpData() {
        if (mViewModel.activityJumpLiveData.hasObservers()) {
            return
        }
        mViewModel.activityJumpLiveData.observe(viewLifecycleOwner) {
            mActivityJumpListener.onActivityJumpLiveDataChange(it)
        }
    }
}

