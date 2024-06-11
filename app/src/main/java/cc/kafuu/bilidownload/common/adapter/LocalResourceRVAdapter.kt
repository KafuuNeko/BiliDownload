package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemLocalResourceHolder
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.viewmodel.activity.HistoryDetailsViewModel

class LocalResourceRVAdapter(viewModel: HistoryDetailsViewModel, context: Context) :
    CoreRVAdapter<HistoryDetailsViewModel>(viewModel, context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemLocalResourceHolder(parent)
    }
}