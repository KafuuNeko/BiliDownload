package cc.kafuu.bilidownload.view.activity

import android.content.Intent
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
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
            putExtra("objectType", "video")
            putExtra("object", video)
        }

        fun buildIntent(media: BiliMedia) = Intent().apply {
            putExtra("objectType", "media")
            putExtra("object", media)
        }
    }

    override fun initViews() {
        setImmersionStatusBar()
    }
}