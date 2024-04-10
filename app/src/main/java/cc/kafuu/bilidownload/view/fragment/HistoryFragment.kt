package cc.kafuu.bilidownload.view.fragment

import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.common.adapter.HistoryRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.viewmodel.fragment.HistoryViewModel
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.listener.OnRefreshLoadMoreListener
import kotlinx.coroutines.runBlocking

class HistoryFragment : RVFragment<HistoryViewModel>(HistoryViewModel::class.java) {
    companion object {
        private const val TAG = "HistoryFragment"
        @JvmStatic
        fun newInstance() = HistoryFragment().apply {

        }
    }

    override fun initViews() {
        super.initViews()
        initViewMode()
        initSmartRefreshLayout()
    }

    private fun initViewMode() {
        runBlocking {
            mViewModel.initData(
                DownloadTaskEntity.STATUS_DOWNLOADING,
                DownloadTaskEntity.STATUS_COMPLETED,
                DownloadTaskEntity.STATUS_DOWNLOAD_FAILED
            )
            mViewModel.latestDownloadTaskLiveData.observe(this@HistoryFragment) { list ->
                mViewModel.onNewDataReceived(list.filter { it.id > mViewModel.latestDownloadTaskId })
            }
        }
    }

    private fun initSmartRefreshLayout() {
        setEnableRefresh(true)
        setEnableLoadMore(true)

        setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onRefresh(refreshLayout: RefreshLayout) {
                runBlocking { mViewModel.fetchHistory() }
                refreshLayout.finishRefresh(true)
            }
            override fun onLoadMore(refreshLayout: RefreshLayout) {
                runBlocking { mViewModel.fetchHistory(10, mViewModel.oldestDownloadTaskId) }
                refreshLayout.finishLoadMore(true)
            }
        })
    }

    override fun getRVAdapter(): CoreRVAdapter<*> = HistoryRVAdapter(mViewModel, requireContext())

    override fun getRVLayoutManager(): RecyclerView.LayoutManager = LinearLayoutManager(context)

}