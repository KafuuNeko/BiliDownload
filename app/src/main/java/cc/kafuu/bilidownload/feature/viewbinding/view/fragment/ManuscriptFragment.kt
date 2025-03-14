package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common.BiliRVFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.ManuscriptViewModel

class ManuscriptFragment : BiliRVFragment<ManuscriptViewModel>(ManuscriptViewModel::class.java) {
    companion object {
        const val KEY_MID = "mid"

        class Builder(val mid: Long) : CoreFragmentBuilder<ManuscriptFragment>() {
            override fun onMallocFragment() = ManuscriptFragment()
            override fun onPreparationArguments() {
                putArgument(KEY_MID, mid)
            }
        }

        @JvmStatic
        fun builder(mid: Long) = Builder(mid)
    }

    private val mAdapter: BiliResourceRVAdapter by lazy {
        BiliResourceRVAdapter(mViewModel, requireContext())
    }

    override fun getRVAdapter() = mAdapter

    override fun initViews() {
        super.initViews()
        mViewModel.initData(arguments?.getLong(FavoriteListFragment.KEY_MID) ?: 0)
        mViewModel.loadData(LoadingStatus.loadingStatus(true), loadMore = false)
    }

}