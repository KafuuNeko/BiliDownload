package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.common.adapter.HistoryRVAdapter
import cc.kafuu.bilidownload.common.adapter.SearchRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.viewmodel.fragment.SearchListViewModel

class SearchListFragment : RVFragment<SearchListViewModel>(SearchListViewModel::class.java) {
    private val mAdapter: SearchRVAdapter by lazy {
        SearchRVAdapter(
            mViewModel, requireContext()
        )
    }

    override fun getRVAdapter() = mAdapter

    override fun getRVLayoutManager() = LinearLayoutManager(context)

    fun doSearch(keyword: String) {
        mViewModel.doSearch(keyword)
    }
}