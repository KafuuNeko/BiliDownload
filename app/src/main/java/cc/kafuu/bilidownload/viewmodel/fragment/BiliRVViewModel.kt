package cc.kafuu.bilidownload.viewmodel.fragment

import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.view.activity.VideoDetailsActivity

open class BiliRVViewModel: RVViewModel() {
    /**
     * 进入视频详情页（视频）
     */
    fun enterDetails(element: BiliVideoModel) {
        startActivity(VideoDetailsActivity::class.java, VideoDetailsActivity.buildIntent(element))
    }

    /**
     * 进入视频详情页（媒体）
     */
    fun enterDetails(element: BiliMediaModel) {
        startActivity(VideoDetailsActivity::class.java, VideoDetailsActivity.buildIntent(element))
    }
}