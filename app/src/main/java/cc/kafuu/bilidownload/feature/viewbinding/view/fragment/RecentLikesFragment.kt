package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common.BiliRVFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.RecentLikesViewModel

class RecentLikesFragment : BiliRVFragment<RecentLikesViewModel>(
    RecentLikesViewModel::class.java
) {
    companion object {
        private const val KEY_MID = "mid"

        class Builder(private val mid: Long) : CoreFragmentBuilder<RecentLikesFragment>() {
            override fun onMallocFragment() = RecentLikesFragment()

            override fun onPreparationArguments() {
                putArgument(KEY_MID, mid)
            }
        }

        @JvmStatic
        fun builder(mid: Long) = Builder(mid)
    }

    override fun initViews() {
        super.initViews()
        setEnableRefresh(true)
        setEnableLoadMore(false)
        mViewModel.initData(arguments?.getLong(KEY_MID) ?: 0L)
    }
}
