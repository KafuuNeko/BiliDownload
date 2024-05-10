package cc.kafuu.bilidownload.view.dialog

import android.annotation.SuppressLint
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
import cc.kafuu.bilidownload.model.ConfirmDialogStatus
import cc.kafuu.bilidownload.model.ResourceType
import cc.kafuu.bilidownload.model.bili.BiliResourceItem
import cc.kafuu.bilidownload.model.popmessage.ToastMessage
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
            videoList = videoResources.orEmpty().map { BiliResourceItem(it, ResourceType.VIDEO) }
            audioList = audioResources.orEmpty().map { BiliResourceItem(it, ResourceType.AUDIO) }
            confirmCallback = callback
        }
    }


    var titleText: String? = null
    var videoList: List<BiliResourceItem>? = null
    var audioList: List<BiliResourceItem>? = null
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

    @SuppressLint("NotifyDataSetChanged")
    private fun initViewMode() {
        mViewModel.titleLiveData.value = titleText
        mViewModel.videoResourcesLiveData.value = videoList
        mViewModel.audioResourcesLiveData.value = audioList
        mViewModel.confirmCallback = confirmCallback

        mViewModel.dialogStatusLiveData.observe(this) {
            onDialogStatusChanged(it)
        }

        mViewModel.currentVideoResourceLiveData.observe(this) {
            mVideoListAdapter.notifyDataSetChanged()
        }
        mViewModel.currentAudioResourceLiveData.observe(this) {
            mAudioListAdapter.notifyDataSetChanged()
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
            ConfirmDialogStatus.CLOSED -> dismiss()
            ConfirmDialogStatus.CONFIRMING -> onConfirm()
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
        mViewModel.dialogStatusLiveData.value = ConfirmDialogStatus.CLOSED
    }

}