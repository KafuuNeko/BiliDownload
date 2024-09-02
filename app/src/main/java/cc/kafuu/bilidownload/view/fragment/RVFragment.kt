package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreFragment
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.manager.RVToTopVisibleListener
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.databinding.FragmentRvBinding
import cc.kafuu.bilidownload.viewmodel.common.RVViewModel
import com.scwang.smart.refresh.layout.listener.OnRefreshListener
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

abstract class RVFragment<VM : RVViewModel>(
    vmClass: Class<VM>
) : CoreFragment<FragmentRvBinding, VM>(
    vmClass,
    R.layout.fragment_rv,
    BR.viewModel
) {
    private var mEnableRefresh: Boolean = true
    private var mEnableLoadMore: Boolean = true

    abstract fun getRVAdapter(): CoreRVAdapter<*>?
    abstract fun getRVLayoutManager(): androidx.recyclerview.widget.RecyclerView.LayoutManager?

    override fun initViews() {
        mViewDataBinding.initViews()
        mViewModel.init()
    }

    private fun FragmentRvBinding.initViews() {
        rvContent.apply {
            addOnScrollListener(RVToTopVisibleListener(ivTop))
            layoutManager = getRVLayoutManager()
            adapter = getRVAdapter()
        }
        ivTop.setOnClickListener { resetScrollPosition(false) }
    }

    private fun RVViewModel.init() {
        loadingStatusMessageMutableLiveData.observe(this@RVFragment) {
            onLoadingStatusChange(it)
        }
    }

    fun setEnableRefresh(enable: Boolean) {
        mEnableRefresh = enable
        mViewDataBinding.refreshLayout.setEnableRefresh(enable)
    }

    fun setEnableLoadMore(enable: Boolean) {
        mEnableLoadMore = enable
        mViewDataBinding.refreshLayout.setEnableLoadMore(enable)
    }

    fun setOnRefreshListener(refreshListener: OnRefreshListener?) {
        mViewDataBinding.refreshLayout.setOnRefreshListener(refreshListener)
    }

    fun setOnRefreshLoadMoreListener(refreshLoadMoreListener: OnRefreshLoadMoreListener?) {
        mViewDataBinding.refreshLayout.setOnRefreshLoadMoreListener(refreshLoadMoreListener)
    }

    open fun resetScrollPosition(autoRefresh: Boolean) {
        mViewDataBinding.rvContent.apply {
            val manager = layoutManager as LinearLayoutManager
            manager.scrollToPosition(0)
            smoothScrollToPosition(0)
        }
        if (autoRefresh) {
            mViewDataBinding.refreshLayout.autoRefresh()
        }
    }

    private fun onLoadingStatusChange(status: LoadingStatus) {
        val sc = status.statusCode
        val enableRefresh = (sc != LoadingStatus.CODE_WAIT && sc != LoadingStatus.CODE_LOADING)
        mViewDataBinding.refreshLayout.setEnableRefresh(mEnableRefresh && enableRefresh)
        mViewDataBinding.refreshLayout.setEnableLoadMore(mEnableLoadMore && enableRefresh)
    }
}