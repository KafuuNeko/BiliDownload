package cc.kafuu.bilidownload.view.dialog

import android.graphics.drawable.Drawable
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreBasicsDialog
import cc.kafuu.bilidownload.databinding.DialogConfirmBinding

typealias ConfirmDialogCallback = (() -> Boolean)

class ConfirmDialog : CoreBasicsDialog<DialogConfirmBinding>(R.layout.dialog_confirm) {

    var leftClickCallback: ConfirmDialogCallback? = null
    var rightClickCallback: ConfirmDialogCallback? = null

    var leftButtonTextColor: Int
        get() = mViewDataBinding.tvBtnLeft.currentTextColor
        set(value) = mViewDataBinding.tvBtnLeft.setTextColor(value)

    var rightButtonTextColor: Int
        get() = mViewDataBinding.tvBtnRight.currentTextColor
        set(value) = mViewDataBinding.tvBtnRight.setTextColor(value)

    var leftButtonBackgroundDrawable: Drawable
        get() = mViewDataBinding.tvBtnLeft.background
        set(value) = mViewDataBinding.tvBtnLeft.setBackgroundDrawable(value)

    var rightButtonBackgroundDrawable: Drawable
        get() = mViewDataBinding.tvBtnRight.background
        set(value) = mViewDataBinding.tvBtnRight.setBackgroundDrawable(value)

    override fun initViews() {
        mViewDataBinding.tvBtnLeft.setOnClickListener {
            if (leftClickCallback?.invoke() == true) {
                dismiss()
            }
        }
        mViewDataBinding.tvBtnRight.setOnClickListener {
            if (rightClickCallback?.invoke() == true) {
                dismiss()
            }
        }
    }
}