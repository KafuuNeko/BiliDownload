package cc.kafuu.bilidownload.common.core

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModelProvider
import cc.kafuu.bilidownload.common.manager.PopMessageManager
import cc.kafuu.bilidownload.common.model.ActivityJumpData
import cc.kafuu.bilidownload.common.model.popmessage.PopMessage


abstract class CoreAdvancedDialog<V : ViewDataBinding, VM : CoreViewModel>(
    private val vmClass: Class<VM>,
    @LayoutRes val layoutId: Int,
    private val viewModelId: Int
) : CoreBasicsDialog<V>(layoutId) {
    protected lateinit var mViewModel: VM

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
            onActivityJumpLiveDataChange(it)
        }
    }

    /**
     * 当活动跳转的 LiveData 发生变化时被调用，处理Fragment跳转逻辑。
     *
     * @param jumpData Fragment跳转的数据，包含了目标活动和其他跳转信息。
     */
    private fun onActivityJumpLiveDataChange(jumpData: cc.kafuu.bilidownload.common.model.ActivityJumpData) {
        if (jumpData.isDeprecated) {
            return
        }

        jumpData.targetClass?.let { targetClass ->
            (jumpData.targetIntent ?: Intent()).apply {
                component = ComponentName(requireActivity(), targetClass)
            }.also { targetIntent ->
                startActivity(targetIntent)
            }
        }

        if (jumpData.finishCurrent) {
            jumpData.activityResult?.let { activityResult ->
                requireActivity().setResult(activityResult.resultCode, activityResult.data)
            }
            requireActivity().finish()
        }

        jumpData.isDeprecated = true
    }
}

