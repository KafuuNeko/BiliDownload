package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.common.adapter.HistoryRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.viewmodel.fragment.HistoryViewModel
import kotlinx.coroutines.runBlocking

class HistoryFragment : RVFragment<HistoryViewModel>(HistoryViewModel::class.java) {

    override fun initViews() {
        super.initViews()
    }

    override fun onStart() {
        super.onStart()
        runBlocking { mViewModel.fetchHistory(intArrayOf(DownloadTaskEntity.STATUS_DOWNLOADING, DownloadTaskEntity.STATUS_COMPLETED, DownloadTaskEntity.STATUS_DOWNLOAD_FAILED)) }
    }

    override fun getRVAdapter(): CoreRVAdapter<*> = HistoryRVAdapter(mViewModel, requireContext())

    override fun getRVLayoutManager(): RecyclerView.LayoutManager = LinearLayoutManager(context)

    companion object {
        @JvmStatic
        fun newInstance() = HistoryFragment().apply {

        }
    }
}