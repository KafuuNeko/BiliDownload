package cc.kafuu.bilidownload.feature.viewbinding.view.dialog

import android.os.Bundle
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.viewbinding.dialog.CoreBasicsDialog
import cc.kafuu.bilidownload.databinding.DialogLoadingBinding

class LoadingDialog : CoreBasicsDialog<DialogLoadingBinding, Boolean>(R.layout.dialog_loading) {

    companion object {
        const val KEY_MESSAGE = "message"

        fun buildDialog(message: String? = null) = LoadingDialog().apply {
            arguments = Bundle().apply {
                putString(KEY_MESSAGE, message)
            }
        }
    }

    private var mMessage: String? = null

    override fun initViews() {
        mViewDataBinding.init()
    }

    private fun DialogLoadingBinding.init() {
        initArguments()
        mMessage?.let {
            tvMessage.text = it
        } ?: run {
            tvMessage.text = context?.getString(R.string.default_remind_loading)
        }
        isCancelable = false
    }

    private fun initArguments() {
        mMessage = arguments?.getString(KEY_MESSAGE)
    }
}
