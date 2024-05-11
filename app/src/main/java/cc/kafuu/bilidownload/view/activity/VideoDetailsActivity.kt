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
import cc.kafuu.bilidownload.common.utils.SerializationUtils.getSerializable
import cc.kafuu.bilidownload.databinding.ActivityVideoDetailsBinding
import cc.kafuu.bilidownload.model.bili.BiliMediaDetails
import cc.kafuu.bilidownload.model.bili.BiliVideoDetails
import cc.kafuu.bilidownload.model.bili.BiliVideoPart
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

        fun buildIntent(video: BiliVideoDetails) = Intent().apply {
            putExtra(KEY_OBJECT_TYPE, BiliVideoDetails::class.simpleName)
            putExtra(KEY_OBJECT_INSTANCE, video)
        }

        fun buildIntent(media: BiliMediaDetails) = Intent().apply {
            putExtra(KEY_OBJECT_TYPE, BiliMediaDetails::class.simpleName)
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
            createBiliPartDialog(it.first, it.second).show(supportFragmentManager, null)
        }
    }

    private fun createBiliPartDialog(
        part: BiliVideoPart,
        dash: BiliPlayStreamDash
    ) = BiliPartDialog.buildDialog(
        part.name,
        dash.video,
        dash.audio
    ) { selectedVideo, selectedAudio ->
        Log.d(TAG, "selected: $selectedVideo, $selectedAudio")
        mCoroutineScope.launch {
            DownloadManager.startDownload(
                this@VideoDetailsActivity,
                part.bvid,
                part.cid,
                selectedVideo,
                selectedAudio
            )
        }
    }

    private fun doInitData() = when (intent.getStringExtra(KEY_OBJECT_TYPE)) {
        BiliVideoDetails::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializable(KEY_OBJECT_INSTANCE, BiliVideoDetails::class.java)
            )
            true
        }

        BiliMediaDetails::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializable(KEY_OBJECT_INSTANCE, BiliMediaDetails::class.java)
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