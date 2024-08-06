package cc.kafuu.bilidownload.view.dialog

import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.PartResourceRVAdapter
import cc.kafuu.bilidownload.common.constant.ConfirmDialogStatus
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.core.dialog.CoreAdvancedDialog
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.ext.putArgument
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliStreamResourceModel
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.databinding.DialogBiliPartBinding
import cc.kafuu.bilidownload.viewmodel.dialog.BiliPartViewModel

private typealias BiliPartDialogResult = Pair<BiliPlayStreamResource?, BiliPlayStreamResource?>

class BiliPartDialog :
    CoreAdvancedDialog<DialogBiliPartBinding, BiliPartDialogResult, BiliPartViewModel>(
        BiliPartViewModel::class.java,
        R.layout.dialog_bili_part,
        BR.viewModel
    ) {
    companion object {
        const val KEY_TITLE_TEXT = "title_text"
        const val KEY_VIDEO_RESOURCES = "video_resources"
        const val KEY_AUDIO_RESOURCES = "audio_resources"

        fun buildDialog(
            title: String,
            videoResources: List<BiliPlayStreamResource>?,
            audioResources: List<BiliPlayStreamResource>?
        ) = BiliPartDialog().apply {
            arguments = Bundle()
                .putArgument(KEY_TITLE_TEXT, title)
                .putArgument(KEY_VIDEO_RESOURCES, videoResources.orEmpty().map {
                    BiliStreamResourceModel(it, DashType.VIDEO)
                }.toTypedArray())
                .putArgument(KEY_AUDIO_RESOURCES, audioResources.orEmpty().map {
                    BiliStreamResourceModel(it, DashType.AUDIO)
                }.toTypedArray())
        }
    }


    private var mTitleText: String? = null
    private var mVideoList: Array<BiliStreamResourceModel>? = null
    private var mAudioList: Array<BiliStreamResourceModel>? = null

    private lateinit var mAudioListAdapter: PartResourceRVAdapter
    private lateinit var mVideoListAdapter: PartResourceRVAdapter

    override fun initViews() {
        initArguments()
        initViewMode()
        initList()
    }

    private fun initArguments() {
        mTitleText = arguments?.getString(KEY_TITLE_TEXT)
        mVideoList = arguments?.getSerializableByClass<Array<BiliStreamResourceModel>>(
            KEY_VIDEO_RESOURCES
        )
        mAudioList = arguments?.getSerializableByClass<Array<BiliStreamResourceModel>>(
            KEY_AUDIO_RESOURCES
        )
    }

    private fun initViewMode() {
        mTitleText?.also { mViewModel.updateTitle(it) }
        mVideoList?.also { mViewModel.updateVideoResources(it.asList()) }
        mAudioList?.also { mViewModel.updateAudioResources(it.asList()) }

        mViewModel.dialogStatusLiveData.observe(this) {
            onDialogStatusChanged(it)
        }
        mViewModel.currentVideoResourceLiveData.observe(this) {
            it?.let { onVideoSelectStatusChanged(it) }
            updateConfirmText()
        }
        mViewModel.currentAudioResourceLiveData.observe(this) {
            it?.let { onAudioSelectStatusChanged(it) }
            updateConfirmText()
        }
        mViewModel.previousResourceLiveData.observe(this) {
            when (it?.type ?: return@observe) {
                DashType.VIDEO -> onVideoSelectStatusChanged(it)
                DashType.AUDIO -> onAudioSelectStatusChanged(it)
            }
        }
    }

    private fun initList() {
        mVideoListAdapter = PartResourceRVAdapter(mViewModel, requireContext())
        mAudioListAdapter = PartResourceRVAdapter(mViewModel, requireContext())

        mViewDataBinding.rvVideoSelectList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = mVideoListAdapter
        }

        mViewDataBinding.rvAudioSelectList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = mAudioListAdapter
        }
    }

    private fun onDialogStatusChanged(@ConfirmDialogStatus status: Int) {
        when (status) {
            ConfirmDialogStatus.CLOSED -> dismissWithResult()
            ConfirmDialogStatus.CONFIRMING -> onConfirm()
            ConfirmDialogStatus.WAITING -> Unit
        }
    }

    private fun onConfirm() {
        mViewModel.popMessage(
            ToastMessageAction(
                CommonLibs.getString(R.string.text_added_download_queue),
                Toast.LENGTH_SHORT
            )
        )
        BiliPartDialogResult(
            mViewModel.currentVideoResourceLiveData.value?.resource,
            mViewModel.currentAudioResourceLiveData.value?.resource
        ).also { dismissWithResult(it) }
    }

    private fun onVideoSelectStatusChanged(item: BiliStreamResourceModel) {
        val index = mViewModel.videoResourcesLiveData.value?.indexOf(item) ?: return
        mVideoListAdapter.notifyItemChanged(index)
        updateConfirmText()
    }

    private fun onAudioSelectStatusChanged(item: BiliStreamResourceModel) {
        val index = mViewModel.audioResourcesLiveData.value?.indexOf(item) ?: return
        mAudioListAdapter.notifyItemChanged(index)
    }

    private fun updateConfirmText() {
        val audio = mViewModel.currentAudioResourceLiveData.value
        val video = mViewModel.currentVideoResourceLiveData.value
        CommonLibs.getString(
            if (audio == null && video == null) {
                R.string.text_resource_not_selected
            } else if (audio == null) {
                R.string.text_resource_only_video
            } else if (video == null) {
                R.string.text_resource_only_audio
            } else {
                R.string.text_resource_download
            }
        ).also { mViewModel.updateConfirmText(it) }
    }
}