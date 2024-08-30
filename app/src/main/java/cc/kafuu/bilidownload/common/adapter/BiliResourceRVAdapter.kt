package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemBiliMediaHolder
import cc.kafuu.bilidownload.common.adapter.holder.ItemBiliVideoHolder
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.constant.BiliArchiveViewType
import cc.kafuu.bilidownload.viewmodel.fragment.BiliRVViewModel

class BiliResourceRVAdapter(viewModel: BiliRVViewModel, context: Context) :
    CoreRVAdapter<BiliRVViewModel>(viewModel, context) {

    override fun getItemViewType(position: Int) = when(getItemData(position)) {
        is BiliVideoModel -> BiliArchiveViewType.VIDEO_VIEW
        is BiliMediaModel -> BiliArchiveViewType.MEDIA_VIEW
        else -> throw IllegalArgumentException("Unknown view type")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when(viewType) {
        BiliArchiveViewType.VIDEO_VIEW -> ItemBiliVideoHolder(parent)
        BiliArchiveViewType.MEDIA_VIEW-> ItemBiliMediaHolder(parent)
        else -> throw IllegalArgumentException("Unknown view type")
    }
}