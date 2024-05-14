package cc.kafuu.bilidownload.view.activity

import android.content.Intent
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.VideoPartRVAdapter
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.model.DashType
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.utils.SerializationUtils.getSerializable
import cc.kafuu.bilidownload.databinding.ActivityVideoDetailsBinding
import cc.kafuu.bilidownload.view.dialog.BiliPartDialog
import cc.kafuu.bilidownload.viewmodel.activity.VideoDetailsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoDetailsActivity : CoreActivity<ActivityVideoDetailsBinding, VideoDetailsViewModel>(
    VideoDetailsViewModel::class.java,
    R.layout.activity_video_details,
    BR.viewModel
) {
    companion object {
        private const val TAG = "VideoDetailsActivity"

        private const val KEY_OBJECT_TYPE = "objectType"
        private const val KEY_OBJECT_INSTANCE = "objectInstance"

        fun buildIntent(video: BiliVideoModel) = Intent().apply {
            putExtra(KEY_OBJECT_TYPE, BiliVideoModel::class.simpleName)
            putExtra(KEY_OBJECT_INSTANCE, video)
        }

        fun buildIntent(media: BiliMediaModel) = Intent().apply {
            putExtra(KEY_OBJECT_TYPE, BiliMediaModel::class.simpleName)
            putExtra(KEY_OBJECT_INSTANCE, media)
        }
    }

    private val mCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun initViews() {
        setImmersionStatusBar()
        if (!doInitData()) {
            mViewModel.finishActivity()
            return
        }
        initList()
        mViewModel.selectedBiliPlayStreamDashLiveData.observe(this) {
            val part = mViewModel.selectedVideoPartLiveData.value ?: return@observe
            createBiliPartDialog(part, it).show(supportFragmentManager, null)
        }
        mViewModel.loadingVideoPartLiveData.observe(this) {
            onItemLoadingStatusChanged(
                it ?: mViewModel.selectedVideoPartLiveData.value ?: return@observe
            )
        }
    }

    private fun createBiliPartDialog(
        part: BiliVideoPartModel,
        dash: BiliPlayStreamDash
    ) = BiliPartDialog.buildDialog(
        part.name,
        dash.video,
        dash.getAllAudio()
    ) { selectedVideo, selectedAudio ->
        Log.d(TAG, "selected: $selectedVideo, $selectedAudio")

        val resources: List<BiliDashModel> = mutableListOf<BiliDashModel>().apply {
            selectedVideo?.let { add(BiliDashModel.create(DashType.VIDEO, it)) }
            selectedAudio?.let { add(BiliDashModel.create(DashType.AUDIO, it)) }
        }

        mCoroutineScope.launch {
            DownloadManager.startDownload(
                this@VideoDetailsActivity,
                part.bvid,
                part.cid,
                resources
            )
        }
    }

    private fun doInitData() = when (intent.getStringExtra(KEY_OBJECT_TYPE)) {
        BiliVideoModel::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializable(KEY_OBJECT_INSTANCE, BiliVideoModel::class.java)
            )
            true
        }

        BiliMediaModel::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializable(KEY_OBJECT_INSTANCE, BiliMediaModel::class.java)
            )
            true
        }

        else -> false
    }

    private fun initList() {
        mViewDataBinding.rvParts.apply {
            adapter = VideoPartRVAdapter(mViewModel, this@VideoDetailsActivity)
            layoutManager = LinearLayoutManager(this@VideoDetailsActivity)
        }
    }

    private fun onItemLoadingStatusChanged(part: BiliVideoPartModel) {
        mViewDataBinding.rvParts.adapter?.notifyItemChanged(
            mViewModel.biliVideoPageListLiveData.value?.indexOf(part) ?: return
        )
    }
}