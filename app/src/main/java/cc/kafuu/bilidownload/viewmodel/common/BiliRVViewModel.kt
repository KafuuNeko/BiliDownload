package cc.kafuu.bilidownload.viewmodel.common

import cc.kafuu.bilidownload.common.model.bili.BiliFavoriteModel
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.view.activity.FavoriteDetailsActivity
import cc.kafuu.bilidownload.view.activity.VideoDetailsActivity

open class BiliRVViewModel : RVViewModel() {

    /**
     * 刷新数据列表
     */
    open fun onRefreshData(
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) = Unit

    /**
     * 加载更多数据
     */
    open fun onLoadMoreData(
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) = Unit

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

    /**
     * 进入收藏夹详情页
     */
    fun enterDetails(element: BiliFavoriteModel) {
        startActivity(
            FavoriteDetailsActivity::class.java,
            FavoriteDetailsActivity.buildIntent(element)
        )
    }
}