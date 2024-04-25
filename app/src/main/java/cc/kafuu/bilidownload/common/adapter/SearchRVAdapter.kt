package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemSearchResultHolder
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.viewmodel.fragment.SearchListViewModel

class SearchRVAdapter(viewModel: SearchListViewModel, context: Context) :
    CoreRVAdapter<SearchListViewModel>(viewModel, context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemSearchResultHolder(parent)
    }
}