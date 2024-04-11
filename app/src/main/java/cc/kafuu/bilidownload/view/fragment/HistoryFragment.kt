package cc.kafuu.bilidownload.view.fragment

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.common.adapter.HistoryRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.viewmodel.fragment.HistoryViewModel

class HistoryFragment :
    RVFragment<HistoryViewModel>(HistoryViewModel::class.java) {
    companion object {
        private const val TAG = "HistoryFragment"

        @JvmStatic
        fun newInstance(vararg statuses: Int) = HistoryFragment().apply {
            arguments = Bundle().apply {
                putIntArray("statuses", statuses)
            }
        }
    }

    private lateinit var mStatuses: IntArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mStatuses = arguments?.getIntArray("statuses") ?: intArrayOf()
    }

    override fun initViews() {
        super.initViews()
        initViewMode()
        initSmartRefreshLayout()
    }

    private fun initViewMode() {
        mViewModel.initData(*mStatuses)
        mViewModel.latestDownloadTaskLiveData.observe(this) {
            mViewModel.listMutableLiveData.value = it.toMutableList()
        }
    }

    private fun initSmartRefreshLayout() {
        setEnableRefresh(false)
        setEnableLoadMore(false)
    }

    override fun getRVAdapter(): CoreRVAdapter<*> = HistoryRVAdapter(mViewModel, requireContext())

    override fun getRVLayoutManager(): RecyclerView.LayoutManager = LinearLayoutManager(context)

}