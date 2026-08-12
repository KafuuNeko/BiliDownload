package cc.kafuu.bilidownload.feature.viewbinding.view.fragment.common

import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliRVViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshListener
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

abstract class BiliRVFragment<VM : BiliRVViewModel>(
    vmClass: Class<VM>
) : RVFragment<VM>(vmClass), OnRefreshListener, OnRefreshLoadMoreListener {
    override fun initViews() {
        super.initViews()
        setOnRefreshListener(this)
        setOnRefreshLoadMoreListener(this)
    }

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
