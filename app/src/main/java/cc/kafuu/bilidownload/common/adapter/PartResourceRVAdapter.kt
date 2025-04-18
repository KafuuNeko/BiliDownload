package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemPartResourceHolder
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVHolder
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.dialog.BiliPartViewModel

class PartResourceRVAdapter(viewModel: BiliPartViewModel, context: Context) :
    CoreRVAdapter<BiliPartViewModel>(viewModel, context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemPartResourceHolder(parent)
    }
}