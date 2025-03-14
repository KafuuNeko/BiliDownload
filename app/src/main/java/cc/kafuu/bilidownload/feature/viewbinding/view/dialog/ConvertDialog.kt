package cc.kafuu.bilidownload.feature.viewbinding.view.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.ConvertSelectRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.dialog.CoreAdvancedDialog
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.ext.putArguments
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.av.AVCodec
import cc.kafuu.bilidownload.common.model.av.AVFormat
import cc.kafuu.bilidownload.databinding.DialogConvertBinding
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.dialog.ConvertViewModel
import java.io.Serializable

// <视频编码, 音频编码, 新文件后缀名>

class ConvertDialog :
    CoreAdvancedDialog<DialogConvertBinding, ConvertDialog.Companion.Result, ConvertViewModel>(
        ConvertViewModel::class.java,
        R.layout.dialog_convert,
        BR.viewModel
    ) {
    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_CURRENT_FORMAT = "current_format"
        private const val KEY_CURRENT_VIDEO_CODEC = "current_video_codec"
        private const val KEY_CURRENT_AUDIO_CODEC = "current_audio_codec"

        fun buildDialog(
            title: String,
            currentFormat: AVFormat,
            currentAudioCodec: AVCodec?,
            currentVideoCodec: AVCodec?,
        ) = ConvertDialog().apply {
            arguments = Bundle().putArguments(
                KEY_TITLE to title,
                KEY_CURRENT_FORMAT to currentFormat,
                KEY_CURRENT_VIDEO_CODEC to currentVideoCodec,
                KEY_CURRENT_AUDIO_CODEC to currentAudioCodec,
            )
        }

        data class Result(
            val format: AVFormat,
            val videoCodec: AVCodec?,
            val audioCodec: AVCodec?,
            val saveNewFile: Boolean
        ) : Serializable
    }

    override fun initViews() {
        mViewDataBinding.initViewModel()
        mViewModel.initViews()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun DialogConvertBinding.initViewModel() {
        listOf(
            rvFormatSelectList,
            rvVideoSelectList,
            rvAudioSelectList
        ).forEach {
            it.layoutManager = GridLayoutManager(requireContext(), 3)
            it.adapter = ConvertSelectRVAdapter(mViewModel, requireContext())
        }

        mViewModel.formatLiveData.observe(this@ConvertDialog) {
            rvFormatSelectList.adapter?.notifyDataSetChanged()
        }

        mViewModel.audioCodecLiveData.observe(this@ConvertDialog) {
            rvAudioSelectList.adapter?.notifyDataSetChanged()
        }

        mViewModel.videoCodecLiveData.observe(this@ConvertDialog) {
            rvVideoSelectList.adapter?.notifyDataSetChanged()
        }
    }

    private fun ConvertViewModel.initViews() {
        val title = arguments?.getString(KEY_TITLE) ?: CommonLibs.getString(R.string.app_name)
        val format = arguments?.getSerializableByClass<AVFormat>(KEY_CURRENT_FORMAT)
        val videoCodec = arguments?.getSerializableByClass<AVCodec>(KEY_CURRENT_VIDEO_CODEC)
        val audioCodec = arguments?.getSerializableByClass<AVCodec>(KEY_CURRENT_AUDIO_CODEC)

        if (format == null || (videoCodec == null && audioCodec == null)) {
            dismissWithResult()
            return
        }

        init(title, format, videoCodec, audioCodec)
    }

    override fun onViewAction(action: ViewAction) {
        when (action) {
            is ConvertViewModel.Companion.ConfirmAction -> onConfirm()
            is ConvertViewModel.Companion.CancelAction -> onCancel()
            else -> super.onViewAction(action)
        }
    }

    private fun onConfirm() {
        val format = mViewModel.formatLiveData.value ?: return
        Result(
            format = format,
            audioCodec = mViewModel.audioCodecLiveData.value,
            videoCodec = mViewModel.videoCodecLiveData.value,
            saveNewFile = true
        ).also {
            dismissWithResult(it)
        }
    }

    private fun onCancel() {
        dismissWithResult()
    }
}