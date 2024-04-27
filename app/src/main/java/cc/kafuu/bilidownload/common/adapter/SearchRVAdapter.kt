package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemSearchMediaHolder
import cc.kafuu.bilidownload.common.adapter.holder.ItemSearchVideoHolder
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.model.BiliMedia
import cc.kafuu.bilidownload.model.BiliVideo
import cc.kafuu.bilidownload.model.SearchResultViewType
import cc.kafuu.bilidownload.viewmodel.fragment.SearchListViewModel

class SearchRVAdapter(viewModel: SearchListViewModel, context: Context) :
    CoreRVAdapter<SearchListViewModel>(viewModel, context) {

    override fun getItemViewType(position: Int) = when(getItemData(position)) {
        is BiliVideo -> SearchResultViewType.VIDEO_VIEW
        is BiliMedia -> SearchResultViewType.MEDIA_VIEW
        else -> throw IllegalArgumentException("Unknown view type")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when(viewType) {
        SearchResultViewType.VIDEO_VIEW -> ItemSearchVideoHolder(parent)
        SearchResultViewType.MEDIA_VIEW-> ItemSearchMediaHolder(parent)
        else -> throw IllegalArgumentException("Unknown view type")
    }
}