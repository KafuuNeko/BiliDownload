package cc.kafuu.bilidownload.view.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.LocalResourceRVAdapter
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.model.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.databinding.ActivityHistoryDetailsBinding
import cc.kafuu.bilidownload.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.viewmodel.activity.HistoryDetailsViewModel
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class HistoryDetailsActivity : CoreActivity<ActivityHistoryDetailsBinding, HistoryDetailsViewModel>(
    HistoryDetailsViewModel::class.java,
    R.layout.activity_history_details,
    BR.viewModel
) {
    companion object {
        private const val TAG = "HistoryDetailsActivity"

        private const val KEY_ENTITY_ID = "entityId"

        fun buildIntent(entityId: Long) = Intent().apply {
            putExtra(KEY_ENTITY_ID, entityId)
        }
    }

    private var mCurrentDialog: DialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun initViews() {
        setImmersionStatusBar()
        mViewDataBinding.initView()
        initData()
    }

    private fun ActivityHistoryDetailsBinding.initView() {
        rvResources.apply {
            layoutManager = LinearLayoutManager(this@HistoryDetailsActivity)
            adapter = LocalResourceRVAdapter(mViewModel, this@HistoryDetailsActivity)
        }
        tvFailedDelete.setOnClickListener {
            onDelete()
        }
        tvHistoryDelete.setOnClickListener {
            onDelete()
        }
    }

    private fun initData() {
        val entityId = intent.getLongExtra(KEY_ENTITY_ID, -1).also {
            Log.d(TAG, "initData: $it")
            if (it == -1L) {
                mViewModel.finishActivity()
                return
            }
        }
        DownloadRepository.queryDownloadTask(entityId).observe(this) {
            mViewModel.updateVideoDetails(it)
        }
        DownloadRepository.queryResourcesLiveDataByTaskEntityId(entityId).observe(this) {
            mViewModel.updateDownloadResources(it)
        }
    }

    private fun onDelete() {
        if (mCurrentDialog?.isAdded == true) {
            mCurrentDialog?.dismiss()
        }

        mCurrentDialog = ConfirmDialog.buildDialog(
            CommonLibs.getString(R.string.text_delete_confirm),
            CommonLibs.getString(R.string.delete_task_message),
            CommonLibs.getString(R.string.text_cancel),
            CommonLibs.getString(R.string.text_delete),
        ) {
            mViewModel.deleteDownloadTask()
            true
        }.apply {
            rightButtonTextColor = CommonLibs.getColor(R.color.white)
            rightButtonBackground = CommonLibs.getDrawable(R.drawable.shape_button_delete)
        }
        mCurrentDialog?.show(supportFragmentManager, null)
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onDownloadStatusChangeEvent(event: DownloadStatusChangeEvent) {
        if (event.entity.id != mViewModel.downloadDetailsLiveData.value?.downloadTask?.id) return
        mViewModel.updateDownloadStatus()
    }

}