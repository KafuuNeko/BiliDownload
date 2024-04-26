package cc.kafuu.bilidownload.view.fragment

import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.common.adapter.SearchRVAdapter
import cc.kafuu.bilidownload.model.LoadingStatus
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

    private fun onLoadingStatusChange(status: LoadingStatus) {
        val statusCode = status.statusCode
        val refreshEnabled = (
                statusCode != LoadingStatus.CODE_WAIT && statusCode != LoadingStatus.CODE_LOADING)
        Log.d(TAG, "onLoadingStatusChange: $status")
        setEnableRefresh(refreshEnabled)
        setEnableLoadMore(refreshEnabled)
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