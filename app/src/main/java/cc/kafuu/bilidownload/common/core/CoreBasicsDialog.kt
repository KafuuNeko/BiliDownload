package cc.kafuu.bilidownload.common.core

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.DialogFragment
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.utils.CommonLibs


abstract class CoreBasicsDialog<V : ViewDataBinding>(
    @LayoutRes private val layoutId: Int
) : DialogFragment() {
    protected lateinit var mViewDataBinding: V
    protected abstract fun initViews()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Dialog)
    }

    override fun onStart() {
        super.onStart()

        dialog?.apply {
            setCanceledOnTouchOutside(false)
            setCancelable(false)
            window?.let {
                val width = CommonLibs.requireContext().resources.displayMetrics.widthPixels * 0.9f
                it.setLayout(
                    width.toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                it.setGravity(Gravity.CENTER)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mViewDataBinding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        return mViewDataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }
}

