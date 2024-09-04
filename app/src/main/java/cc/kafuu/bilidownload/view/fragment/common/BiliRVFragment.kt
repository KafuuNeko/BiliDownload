package cc.kafuu.bilidownload.view.fragment.common

import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.viewmodel.common.BiliRVViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshListener
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

open class BiliRVFragment<VM : BiliRVViewModel>(
    vmClass: Class<VM>
) : RVFragment<VM>(vmClass), OnRefreshListener, OnRefreshLoadMoreListener {
    private val mAdapter: BiliResourceRVAdapter by lazy {
        BiliResourceRVAdapter(mViewModel, requireContext())
    }

    override fun initViews() {
        super.initViews()
        setOnRefreshListener(this)
        setOnRefreshLoadMoreListener(this)
    }

    override fun getRVAdapter(): CoreRVAdapter<*>? = mAdapter

    override fun onRefresh(refreshLayout: RefreshLayout) {
        mViewModel.onRefreshData(
            onSucceeded = { refreshLayout.finishRefresh(true) },
            onFailed = { refreshLayout.finishRefresh(false) }
        )
    }

    override fun onLoadMore(refreshLayout: RefreshLayout) {
        mViewModel.onLoadMoreData(
            onSucceeded = { refreshLayout.finishLoadMore(true) },
            onFailed = { refreshLayout.finishLoadMore(false) }
        )
    }
}