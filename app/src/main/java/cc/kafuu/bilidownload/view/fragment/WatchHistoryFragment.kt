package cc.kafuu.bilidownload.view.fragment

import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.view.fragment.common.BiliRVFragment
import cc.kafuu.bilidownload.viewmodel.fragment.WatchHistoryViewModel

class WatchHistoryFragment : BiliRVFragment<WatchHistoryViewModel>(
    WatchHistoryViewModel::class.java
) {
    companion object {
        class Builder : CoreFragmentBuilder<WatchHistoryFragment>() {
            override fun onMallocFragment() = WatchHistoryFragment()
        }

        @JvmStatic
        fun builder() = Builder()
    }

    override fun initViews() {
        super.initViews()
        mViewModel.loadData(loadingStatus = LoadingStatus.loadingStatus(), loadMore = false)
    }
}