package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemDownloadRecordHolder
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVHolder
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.HistoryViewModel

class DownloadHistoryRVAdapter(viewModel: HistoryViewModel, context: Context) :
    CoreRVAdapter<HistoryViewModel>(viewModel, context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemDownloadRecordHolder(parent)
    }
}