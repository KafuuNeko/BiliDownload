package cc.kafuu.bilidownload.view.fragment

import cc.kafuu.bilidownload.common.constant.SearchType
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.view.fragment.common.BiliRVFragment
import cc.kafuu.bilidownload.viewmodel.fragment.SearchListViewModel

class SearchListFragment : BiliRVFragment<SearchListViewModel>(SearchListViewModel::class.java) {
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