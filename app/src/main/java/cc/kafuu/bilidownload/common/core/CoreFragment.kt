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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cc.kafuu.bilidownload.model.ActivityJumpData

abstract class CoreFragment<V : ViewDataBinding, VM : CoreViewModel>(
    private val vmClass: Class<VM>,
    @LayoutRes private val layoutId: Int,
    private val viewModelId: Int
) : Fragment() {

    protected lateinit var mViewDataBinding: V
    protected lateinit var mViewModel: VM

    abstract fun initViews()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mViewDataBinding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        mViewModel = ViewModelProvider(this)[vmClass]
        if (viewModelId != 0) {
            mViewDataBinding.setVariable(viewModelId, mViewModel)
        }
        mViewDataBinding.lifecycleOwner = this
        initActJumpData()
        initViews()
        return mViewDataBinding.root
    }

    private fun initActJumpData() {
        if (mViewModel.activityJumpLiveData.hasObservers()) {
            return
        }
        mViewModel.activityJumpLiveData.observe(viewLifecycleOwner) {
            onActivityJumpLiveDataChange(it)
        }
    }

    private fun onActivityJumpLiveDataChange(jumpData: ActivityJumpData) {
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