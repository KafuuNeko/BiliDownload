package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.common.adapter.SearchRVAdapter
import cc.kafuu.bilidownload.model.LoadingStatus
import cc.kafuu.bilidownload.model.SearchType
import cc.kafuu.bilidownload.viewmodel.fragment.SearchListViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshListener
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

class SearchListFragment : RVFragment<SearchListViewModel>(SearchListViewModel::class.java),
    OnRefreshListener, OnRefreshLoadMoreListener {
    companion object {
        private const val TAG = "SearchListFragment"
    }

    override fun initViews() {
        super.initViews()
        mViewModel.loadingStatusMessageMutableLiveData.observe(this) {
            onLoadingStatusChange(it)
        }
        setOnRefreshListener(this)
        setOnRefreshLoadMoreListener(this)
    }

    private val mAdapter: SearchRVAdapter by lazy {
        SearchRVAdapter(
            mViewModel, requireContext()
        )
    }

    override fun getRVAdapter() = mAdapter

    override fun getRVLayoutManager() = LinearLayoutManager(context)

    fun doSearch(keyword: String) {
        mViewModel.keyword = keyword
        mViewModel.doSearch(LoadingStatus.loadingStatus(), false)
    }

    fun onSearchTypeChange(@SearchType searchType: Int) {
        val statusCode = mViewModel.loadingStatusMessageMutableLiveData.value?.statusCode
            ?: LoadingStatus.CODE_WAIT
        val needRefresh = mViewModel.searchType != searchType
                && statusCode != LoadingStatus.CODE_WAIT
                && statusCode != LoadingStatus.CODE_LOADING
        mViewModel.searchType = searchType
        if (needRefresh) {
            mViewModel.doSearch(LoadingStatus.loadingStatus(), false)
        }
    }

    private fun onLoadingStatusChange(status: LoadingStatus) {
        val sc = status.statusCode
        val enableRefresh = (sc != LoadingStatus.CODE_WAIT && sc != LoadingStatus.CODE_LOADING)
        setEnableRefresh(enableRefresh)
        setEnableLoadMore(enableRefresh)
    }

    override fun onRefresh(refreshLayout: RefreshLayout) {
        val loadingStatus = LoadingStatus.loadingStatus(false)
        mViewModel.doSearch(loadingStatus, false,
            onSucceeded = { refreshLayout.finishRefresh(true) },
            onFailed = { refreshLayout.finishRefresh(false) }
        )
    }

    override fun onLoadMore(refreshLayout: RefreshLayout) {
        val loadingStatus = LoadingStatus.loadingStatus(false)
        mViewModel.doSearch(loadingStatus, true,
            onSucceeded = { refreshLayout.finishLoadMore(true) },
            onFailed = { refreshLayout.finishLoadMore(false) }
        )
    }
}