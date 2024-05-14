package cc.kafuu.bilidownload.view.dialog

import android.content.res.Configuration
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.PartResourceRVAdapter
import cc.kafuu.bilidownload.common.core.CoreAdvancedDialog
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.databinding.DialogBiliPartBinding
import cc.kafuu.bilidownload.common.model.ConfirmDialogStatus
import cc.kafuu.bilidownload.common.model.DashType
import cc.kafuu.bilidownload.common.model.bili.BiliStreamResourceModel
import cc.kafuu.bilidownload.common.model.popmessage.ToastMessage
import cc.kafuu.bilidownload.viewmodel.dialog.BiliPartDialogCallback
import cc.kafuu.bilidownload.viewmodel.dialog.BiliPartViewModel


class BiliPartDialog : CoreAdvancedDialog<DialogBiliPartBinding, BiliPartViewModel>(
    BiliPartViewModel::class.java,
    R.layout.dialog_bili_part,
    BR.viewModel
) {
    companion object {
        fun buildDialog(
            title: String,
            videoResources: List<BiliPlayStreamResource>?,
            audioResources: List<BiliPlayStreamResource>?,
            callback: BiliPartDialogCallback
        ) = BiliPartDialog().apply {
            titleText = title
            videoList =
                videoResources.orEmpty().map { BiliStreamResourceModel(it, DashType.VIDEO) }
            audioList =
                audioResources.orEmpty().map { BiliStreamResourceModel(it, DashType.AUDIO) }
            confirmCallback = callback
        }
    }


    var titleText: String? = null
    var videoList: List<BiliStreamResourceModel>? = null
    var audioList: List<BiliStreamResourceModel>? = null
    var confirmCallback: BiliPartDialogCallback? = null

    private lateinit var mAudioListAdapter: PartResourceRVAdapter
    private lateinit var mVideoListAdapter: PartResourceRVAdapter

    override fun initViews() {
        initViewMode()
        initList()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dismiss()
    }

    private fun initViewMode() {
        mViewModel.titleLiveData.value = titleText
        mViewModel.videoResourcesLiveData.value = videoList
        mViewModel.audioResourcesLiveData.value = audioList
        mViewModel.confirmCallback = confirmCallback

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

    private fun onDialogStatusChanged(status: Int) {
        when (status) {
            cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.CLOSED -> dismiss()
            cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.CONFIRMING -> onConfirm()
        }
    }

    private fun onConfirm() {
        mViewModel.confirmCallback?.invoke(
            mViewModel.currentVideoResourceLiveData.value?.resource,
            mViewModel.currentAudioResourceLiveData.value?.resource
        )
        mViewModel.popMessage(
            ToastMessage(
                CommonLibs.getString(R.string.text_added_download_queue),
                Toast.LENGTH_SHORT
            )
        )
        mViewModel.dialogStatusLiveData.value = cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.CLOSED
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
        mViewModel.confirmTextLiveData.value = CommonLibs.getString(
            if (audio == null && video == null) {
                R.string.text_resource_not_selected
            } else if (audio == null) {
                R.string.text_resource_only_video
            } else if (video == null) {
                R.string.text_resource_only_audio
            } else {
                R.string.text_resource_download
            }
        )
    }

}