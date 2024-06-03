package cc.kafuu.bilidownload.view.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.model.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.databinding.ActivityHistoryDetailsBinding
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
        initData()
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
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onDownloadStatusChangeEvent(event: DownloadStatusChangeEvent) {
        if (event.entity.id != mViewModel.downloadDetailsLiveData.value?.downloadTask?.id) return
        mViewModel.downloadStatusLiveData.value = event.status
    }

}