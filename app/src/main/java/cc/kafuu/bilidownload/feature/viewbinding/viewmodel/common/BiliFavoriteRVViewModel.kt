package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common

import cc.kafuu.bilidownload.common.model.bili.BiliFavoriteModel
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.FavoriteDetailsActivity

/** 提供收藏夹列表条目的导航行为。 */
open class BiliFavoriteRVViewModel : BiliRVViewModel() {
    fun enterDetails(element: BiliFavoriteModel) {
        startActivity(
            FavoriteDetailsActivity::class.java,
            FavoriteDetailsActivity.buildIntent(element),
        )
    }
}
