package cc.kafuu.bilidownload.view.activity

import android.content.Intent
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.VideoPartRVAdapter
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.utils.SerializationUtils.getSerializable
import cc.kafuu.bilidownload.databinding.ActivityVideoDetailsBinding
import cc.kafuu.bilidownload.model.BiliMedia
import cc.kafuu.bilidownload.model.BiliVideo
import cc.kafuu.bilidownload.viewmodel.activity.VideoDetailsViewModel

class VideoDetailsActivity : CoreActivity<ActivityVideoDetailsBinding, VideoDetailsViewModel>(
    VideoDetailsViewModel::class.java,
    R.layout.activity_video_details,
    BR.viewModel
) {
    companion object {
        fun buildIntent(video: BiliVideo) = Intent().apply {
            putExtra("objectType", BiliVideo::class.simpleName)
            putExtra("objectInstance", video)
        }

        fun buildIntent(media: BiliMedia) = Intent().apply {
            putExtra("objectType", BiliMedia::class.simpleName)
            putExtra("objectInstance", media)
        }
    }

    override fun initViews() {
        setImmersionStatusBar()
        if (!doInitData()) {
            mViewModel.finishActivity()
            return
        }
        mViewDataBinding.rvParts.apply {
            adapter = VideoPartRVAdapter(mViewModel, this@VideoDetailsActivity)
            layoutManager = LinearLayoutManager(this@VideoDetailsActivity)
        }
    }

    private fun doInitData() = when (intent.getStringExtra("objectType")) {
        BiliVideo::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializable("objectInstance", BiliVideo::class.java)
            )
            true
        }

        BiliMedia::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializable("objectInstance", BiliMedia::class.java)
            )
            true
        }

        else -> false
    }
}