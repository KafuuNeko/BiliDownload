package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemPartVideoHolder
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVHolder
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.VideoDetailsViewModel

class VideoPartRVAdapter(viewModel: VideoDetailsViewModel, context: Context) :
    CoreRVAdapter<VideoDetailsViewModel>(viewModel, context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemPartVideoHolder(parent)
    }
}