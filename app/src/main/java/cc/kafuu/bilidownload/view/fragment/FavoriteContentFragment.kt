package cc.kafuu.bilidownload.view.fragment

import androidx.fragment.app.activityViewModels
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.view.fragment.common.BiliRVFragment
import cc.kafuu.bilidownload.viewmodel.activity.FavoriteDetailsViewModel

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