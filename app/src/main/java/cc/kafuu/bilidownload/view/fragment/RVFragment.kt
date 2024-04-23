package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreFragment
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.manager.RVToTopVisibleListener
import cc.kafuu.bilidownload.databinding.FragmentRvBinding
import cc.kafuu.bilidownload.viewmodel.fragment.RVViewModel
import com.scwang.smart.refresh.layout.listener.OnRefreshListener
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener

abstract class RVFragment<VM : RVViewModel>(
    vmClass: Class<VM>
) : CoreFragment<FragmentRvBinding, VM>(
    vmClass,
    R.layout.fragment_rv,
    BR.viewModel
) {
    abstract fun getRVAdapter(): CoreRVAdapter<*>?
    abstract fun getRVLayoutManager(): androidx.recyclerview.widget.RecyclerView.LayoutManager?

    override fun initViews() {
        mViewDataBinding.initViews()
    }

    private fun FragmentRvBinding.initViews() {
        rvContent.apply {
            addOnScrollListener(RVToTopVisibleListener(ivTop))
            layoutManager = getRVLayoutManager()
            adapter = getRVAdapter()
        }
        ivTop.setOnClickListener { resetScrollPosition(false) }
    }

    fun setEnableRefresh(enable: Boolean) {
        mViewDataBinding.refreshLayout.setEnableRefresh(enable)
    }

    fun setEnableLoadMore(enable: Boolean) {
        mViewDataBinding.refreshLayout.setEnableLoadMore(enable)
    }

    fun setOnRefreshListener(refreshListener: OnRefreshListener?) {
        mViewDataBinding.refreshLayout.setOnRefreshListener(refreshListener)
    }

    fun setOnRefreshLoadMoreListener(refreshLoadMoreListener: OnRefreshLoadMoreListener?) {
        mViewDataBinding.refreshLayout.setOnRefreshLoadMoreListener(refreshLoadMoreListener)
    }

    fun resetScrollPosition(autoRefresh: Boolean) {
        mViewDataBinding.rvContent.apply {
            val manager = layoutManager as LinearLayoutManager
            manager.scrollToPosition(0)
            smoothScrollToPosition(0)
        }
        if (autoRefresh) {
            mViewDataBinding.refreshLayout.autoRefresh()
        }
    }
}