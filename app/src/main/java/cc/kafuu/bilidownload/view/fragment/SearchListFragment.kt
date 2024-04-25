package cc.kafuu.bilidownload.view.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.viewmodel.fragment.SearchListViewModel

class SearchListFragment : RVFragment<SearchListViewModel>(SearchListViewModel::class.java) {
    companion object {
        @JvmStatic
        fun newInstance() = SearchListFragment().apply {
        }
    }

    override fun getRVAdapter(): CoreRVAdapter<*>? {
        return null
    }

    override fun getRVLayoutManager() = LinearLayoutManager(context)

    fun doSearch(keyword: String) {
        mViewModel.doSearch(keyword)
    }
}