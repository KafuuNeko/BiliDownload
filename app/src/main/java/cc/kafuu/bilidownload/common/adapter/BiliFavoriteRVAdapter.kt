package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemBiliFavoriteHolder
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVHolder
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliRVViewModel

class BiliFavoriteRVAdapter(viewModel: BiliRVViewModel, context: Context) :
    CoreRVAdapter<BiliRVViewModel>(viewModel, context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemBiliFavoriteHolder(parent)
    }
}