package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common.BiliResourceRVFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.WatchLaterViewModel

class WatchLaterFragment : BiliResourceRVFragment<WatchLaterViewModel>(
    WatchLaterViewModel::class.java
) {
    companion object {
        class Builder : CoreFragmentBuilder<WatchLaterFragment>() {
            override fun onMallocFragment() = WatchLaterFragment()
        }

        @JvmStatic
        fun builder() = Builder()
    }

    override fun initViews() {
        super.initViews()
        setEnableRefresh(true)
        setEnableLoadMore(false)
        mViewModel.initData()
    }
}
