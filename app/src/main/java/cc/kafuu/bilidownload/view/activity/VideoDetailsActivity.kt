package cc.kafuu.bilidownload.view.activity

import android.content.Intent
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.VideoPartRVAdapter
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.manager.DownloadManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliVideoPage
import cc.kafuu.bilidownload.common.utils.SerializationUtils.getSerializable
import cc.kafuu.bilidownload.databinding.ActivityVideoDetailsBinding
import cc.kafuu.bilidownload.model.bili.BiliMedia
import cc.kafuu.bilidownload.model.bili.BiliVideo
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

        fun buildIntent(video: BiliVideo) = Intent().apply {
            putExtra(KEY_OBJECT_TYPE, BiliVideo::class.simpleName)
            putExtra(KEY_OBJECT_INSTANCE, video)
        }

        fun buildIntent(media: BiliMedia) = Intent().apply {
            putExtra(KEY_OBJECT_TYPE, BiliMedia::class.simpleName)
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
            createBiliPartDialog(
                mViewModel.biliVideoLiveData.value!!,
                it.first,
                it.second
            ).show(supportFragmentManager, null)
        }
    }

    private fun createBiliPartDialog(
        video: BiliVideo,
        page: BiliVideoPage,
        dash: BiliPlayStreamDash
    ) = BiliPartDialog.buildDialog(
        page.part,
        dash.video,
        dash.audio
    ) { selectedVideo, selectedAudio ->
        Log.d(TAG, "selected: $selectedVideo, $selectedAudio")
        mCoroutineScope.launch {
            DownloadManager.startDownload(
                this@VideoDetailsActivity,
                video.bvid,
                page.cid,
                selectedVideo,
                selectedAudio
            )
        }
    }

    private fun doInitData() = when (intent.getStringExtra(KEY_OBJECT_TYPE)) {
        BiliVideo::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializable(KEY_OBJECT_INSTANCE, BiliVideo::class.java)
            )
            true
        }

        BiliMedia::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializable(KEY_OBJECT_INSTANCE, BiliMedia::class.java)
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
}