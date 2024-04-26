package cc.kafuu.bilidownload.view.fragment

import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.common.adapter.SearchRVAdapter
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
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
        mViewModel.doSearch(loadingStatus, false, object : IServerCallback<BiliSearchData<BiliSearchVideoResultData>> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliSearchData<BiliSearchVideoResultData>
            ) {
                refreshLayout.finishRefresh(true)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                refreshLayout.finishRefresh(false)
            }
        })
    }

    override fun onLoadMore(refreshLayout: RefreshLayout) {
        val loadingStatus = LoadingStatus.loadingStatus(false)
        mViewModel.doSearch(loadingStatus, true, object : IServerCallback<BiliSearchData<BiliSearchVideoResultData>> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliSearchData<BiliSearchVideoResultData>
            ) {
                refreshLayout.finishLoadMore(true)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                refreshLayout.finishLoadMore(false)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        })
    }
}