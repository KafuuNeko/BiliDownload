package cc.kafuu.bilidownload.view.fragment

import cc.kafuu.bilidownload.common.adapter.BiliFavoriteRVAdapter
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.view.fragment.common.BiliRVFragment
import cc.kafuu.bilidownload.viewmodel.fragment.FavoriteListViewModel

class FavoriteListFragment : BiliRVFragment<FavoriteListViewModel>(
    FavoriteListViewModel::class.java
) {
    companion object {
        const val KEY_MID = "mid"

        class Builder(val mid: Long) : CoreFragmentBuilder<FavoriteListFragment>() {
            override fun onMallocFragment() = FavoriteListFragment()
            override fun onPreparationArguments() {
                putArgument(KEY_MID, mid)
            }
        }

        @JvmStatic
        fun builder(mid: Long) = Builder(mid)
    }

    private val mAdapter: BiliFavoriteRVAdapter by lazy {
        BiliFavoriteRVAdapter(mViewModel, requireContext())
    }

    override fun getRVAdapter() = mAdapter

    override fun initViews() {
        super.initViews()

        setEnableRefresh(true)
        setEnableLoadMore(false)

        mViewModel.initData(arguments?.getLong(KEY_MID) ?: 0)
    }
}