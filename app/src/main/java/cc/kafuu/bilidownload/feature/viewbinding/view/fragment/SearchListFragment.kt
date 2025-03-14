package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import cc.kafuu.bilidownload.common.constant.SearchType
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common.BiliRVFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.SearchListViewModel

class SearchListFragment : BiliRVFragment<cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.SearchListViewModel>(
    cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.SearchListViewModel::class.java) {
    companion object {
        object Builder : CoreFragmentBuilder<SearchListFragment>() {
            override fun onMallocFragment() = SearchListFragment()
        }

        fun builder() = Builder
    }

    fun doSearch(keyword: String) {
        mViewModel.keyword = keyword
        mViewModel.doSearch(LoadingStatus.loadingStatus(), loadMore = false, forceSearch = false)
    }

    fun onSearchTypeChange(@SearchType searchType: Int) {
        val statusCode = mViewModel.loadingStatusMessageMutableLiveData.value?.statusCode
            ?: LoadingStatus.CODE_WAIT
        val needRefresh = mViewModel.searchType != searchType
                && statusCode != LoadingStatus.CODE_WAIT
                && statusCode != LoadingStatus.CODE_LOADING
        mViewModel.searchType = searchType
        if (needRefresh) {
            mViewModel.doSearch(LoadingStatus.loadingStatus(), loadMore = false, forceSearch = true)
        }
    }
}