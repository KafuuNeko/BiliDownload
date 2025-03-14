package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import androidx.fragment.app.activityViewModels
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common.BiliRVFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.FavoriteDetailsViewModel

class FavoriteContentFragment : BiliRVFragment<FavoriteDetailsViewModel>(
    FavoriteDetailsViewModel::class.java
) {
    companion object {
        class Builder : CoreFragmentBuilder<FavoriteContentFragment>() {
            override fun onMallocFragment() = FavoriteContentFragment()
        }

        @JvmStatic
        fun builder() = Builder()
    }

    private val mActivityViewModel by activityViewModels<FavoriteDetailsViewModel>()

    override fun getViewModel() = mActivityViewModel

    override fun initViews() {
        super.initViews()
        mViewModel.loadData(loadingStatus = LoadingStatus.loadingStatus(), loadMore = false)
    }
}