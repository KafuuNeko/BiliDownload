package cc.kafuu.bilidownload.view.dialog

import android.os.Bundle
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.dialog.CoreBasicsDialog
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.ext.putArguments
import cc.kafuu.bilidownload.databinding.DialogConfirmBinding
import java.io.Serializable

class ConfirmDialog : CoreBasicsDialog<DialogConfirmBinding, Boolean>(R.layout.dialog_confirm) {

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_LEFT_BUTTON_TEXT = "left_button_text"
        const val KEY_RIGHT_BUTTON_TEXT = "right_button_text"
        const val KEY_LEFT_BUTTON_STYLE = "left_button_style"
        const val KEY_RIGHT_BUTTON_STYLE = "right_button_style"

        enum class ButtonStyle(
            @ColorRes val buttonTextColor: Int,
            @DrawableRes val buttonBackground: Int
        ) : Serializable {
            General(R.color.common_white, R.drawable.shape_button_general),
            Delete(R.color.common_white, R.drawable.shape_button_red),
            Logout(R.color.common_white, R.drawable.shape_button_red),
            Stop(R.color.common_white, R.drawable.shape_button_red)
        }

        fun buildDialog(
            title: String,
            message: String,
            leftButtonText: String,
            rightButtonText: String,
            leftButtonStyle: ButtonStyle = ButtonStyle.General,
            rightButtonStyle: ButtonStyle = ButtonStyle.General
        ) = ConfirmDialog().apply {
            arguments = Bundle().putArguments(
                KEY_TITLE to title,
                KEY_MESSAGE to message,
                KEY_LEFT_BUTTON_TEXT to leftButtonText,
                KEY_RIGHT_BUTTON_TEXT to rightButtonText,
                KEY_LEFT_BUTTON_STYLE to leftButtonStyle,
                KEY_RIGHT_BUTTON_STYLE to rightButtonStyle
            )
        }
    }

    private var mTitle: String? = null
    private var mMessage: String? = null
    private var mLeftButtonText: String? = null
    private var mRightButtonText: String? = null
    private var mLeftButtonStyle: ButtonStyle = ButtonStyle.General
    private var mRightButtonStyle: ButtonStyle = ButtonStyle.General

    override fun initViews() {
        mViewDataBinding.init()
    }

    private fun DialogConfirmBinding.init() {
        initArguments()
        tvTitle.text = mTitle
        tvMessage.text = mMessage
        tvBtnLeft.apply {
            setOnClickListener {
                dismissWithResult(false)
            }
            mLeftButtonText?.let { text = it }
            setTextColor(CommonLibs.getColor(mLeftButtonStyle.buttonTextColor))
            setBackgroundResource(mLeftButtonStyle.buttonBackground)
        }
        tvBtnRight.apply {
            setOnClickListener {
                dismissWithResult(true)
            }
            mRightButtonText?.let { text = it }
            setTextColor(CommonLibs.getColor(mRightButtonStyle.buttonTextColor))
            setBackgroundResource(mRightButtonStyle.buttonBackground)
        }
    }

    private fun initArguments() {
        mTitle = arguments?.getString(KEY_TITLE)
        mMessage = arguments?.getString(KEY_MESSAGE)
        mLeftButtonText = arguments?.getString(KEY_LEFT_BUTTON_TEXT)
        mRightButtonText = arguments?.getString(KEY_RIGHT_BUTTON_TEXT)
        arguments?.getSerializableByClass<ButtonStyle>(KEY_RIGHT_BUTTON_STYLE)?.let {
            mRightButtonStyle = it
        }
        arguments?.getSerializableByClass<ButtonStyle>(KEY_LEFT_BUTTON_STYLE)?.let {
            mLeftButtonStyle = it
        }
    }
}