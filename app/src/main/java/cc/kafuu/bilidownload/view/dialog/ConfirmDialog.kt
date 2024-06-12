package cc.kafuu.bilidownload.view.dialog

import android.graphics.drawable.Drawable
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreBasicsDialog
import cc.kafuu.bilidownload.databinding.DialogConfirmBinding

typealias ConfirmDialogCallback = (() -> Boolean)

class ConfirmDialog : CoreBasicsDialog<DialogConfirmBinding>(R.layout.dialog_confirm) {

    companion object {
        fun buildDialog(
            title: String,
            message: String,
            leftButtonText: String,
            rightButtonText: String,
            leftClickCallback: ConfirmDialogCallback? = null,
            rightClickCallback: ConfirmDialogCallback? = null
        ) = ConfirmDialog().apply {
            this.title = title
            this.message = message
            this.leftButtonText = leftButtonText
            this.rightButtonText = rightButtonText
            this.leftClickCallback = leftClickCallback
            this.rightClickCallback = rightClickCallback
        }
    }

    var title: String? = null
    var message: String? = null
    var leftButtonText: String? = null
    var rightButtonText: String? = null

    var leftClickCallback: ConfirmDialogCallback? = null
    var rightClickCallback: ConfirmDialogCallback? = null

    var rightButtonTextColor: Int? = null
    var rightButtonBackground: Drawable? = null

    override fun initViews() {
        mViewDataBinding.init()
    }

    private fun DialogConfirmBinding.init() {
        tvTitle.text = title
        tvMessage.text = message

        tvBtnLeft.apply {
            setOnClickListener {
                if (leftClickCallback == null || leftClickCallback?.invoke() == true) {
                    dismiss()
                }
            }
            leftButtonText?.let { this.text = it }
        }

        tvBtnRight.apply {
            setOnClickListener {
                if (rightClickCallback?.invoke() == true) {
                    dismiss()
                }
            }
            rightButtonText?.let { this.text = it }
            rightButtonTextColor?.let { this.setTextColor(it) }
            rightButtonBackground?.let { this.setBackgroundDrawable(rightButtonBackground) }
        }

    }
}