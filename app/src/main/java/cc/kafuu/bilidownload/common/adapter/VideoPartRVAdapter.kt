package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemVideoPartHolder
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.viewmodel.activity.VideoDetailsViewModel

class VideoPartRVAdapter(viewModel: VideoDetailsViewModel, context: Context) :
    CoreRVAdapter<VideoDetailsViewModel>(viewModel, context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemVideoPartHolder(parent)
    }
}