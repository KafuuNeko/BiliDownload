package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.constant.SearchType
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.viewmodel.fragment.SearchListViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshListener
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

class SearchListFragment : RVFragment<SearchListViewModel>(SearchListViewModel::class.java),
    OnRefreshListener, OnRefreshLoadMoreListener {

    companion object {
        object Builder : CoreFragmentBuilder<SearchListFragment>() {
            override fun onMallocFragment() = SearchListFragment()
        }

        fun builder() = Builder
    }

    override fun initViews() {
        super.initViews()
        setOnRefreshListener(this)
        setOnRefreshLoadMoreListener(this)
    }

    private val mAdapter: BiliResourceRVAdapter by lazy {
        BiliResourceRVAdapter(mViewModel, requireContext())
    }

    override fun getRVAdapter() = mAdapter

    override fun getRVLayoutManager() = LinearLayoutManager(context)

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

    override fun onRefresh(refreshLayout: RefreshLayout) {
        val loadingStatus = LoadingStatus.loadingStatus(false)
        mViewModel.doSearch(loadingStatus, loadMore = false, forceSearch = true,
            onSucceeded = { refreshLayout.finishRefresh(true) },
            onFailed = { refreshLayout.finishRefresh(false) }
        )
    }

    override fun onLoadMore(refreshLayout: RefreshLayout) {
        val loadingStatus = LoadingStatus.loadingStatus(false)
        mViewModel.doSearch(loadingStatus, loadMore = true, forceSearch = true,
            onSucceeded = { refreshLayout.finishLoadMore(true) },
            onFailed = { refreshLayout.finishLoadMore(false) }
        )
    }
}