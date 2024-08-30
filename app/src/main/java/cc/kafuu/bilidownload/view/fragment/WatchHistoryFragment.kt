package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.viewmodel.fragment.WatchHistoryViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshListener
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

class WatchHistoryFragment : RVFragment<WatchHistoryViewModel>(WatchHistoryViewModel::class.java),
    OnRefreshListener, OnRefreshLoadMoreListener {
    companion object {
        class Builder : CoreFragmentBuilder<WatchHistoryFragment>() {
            override fun onMallocFragment() = WatchHistoryFragment()
        }

        @JvmStatic
        fun builder() = Builder()
    }

    private val mAdapter: BiliResourceRVAdapter by lazy {
        BiliResourceRVAdapter(mViewModel, requireContext())
    }

    override fun initViews() {
        super.initViews()
        setOnRefreshListener(this)
        setOnRefreshLoadMoreListener(this)
        mViewModel.loadData(loadingStatus = LoadingStatus.loadingStatus(), loadMore = false)
    }

    override fun getRVAdapter() = mAdapter

    override fun getRVLayoutManager() = LinearLayoutManager(context)

    override fun onRefresh(refreshLayout: RefreshLayout) {
        val loadingStatus = LoadingStatus.loadingStatus(false)
        mViewModel.loadData(loadingStatus = loadingStatus, loadMore = false,
            onSucceeded = { refreshLayout.finishRefresh(true) },
            onFailed = { refreshLayout.finishRefresh(false) }
        )
    }

    override fun onLoadMore(refreshLayout: RefreshLayout) {
        val loadingStatus = LoadingStatus.loadingStatus(false)
        mViewModel.loadData(loadingStatus = loadingStatus, loadMore = true,
            onSucceeded = { refreshLayout.finishLoadMore(true) },
            onFailed = { refreshLayout.finishLoadMore(false) }
        )
    }
}