package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemBiliFavoriteHolder
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.viewmodel.fragment.BiliRVViewModel

class BiliFavoriteRVAdapter(viewModel: BiliRVViewModel, context: Context) :
    CoreRVAdapter<BiliRVViewModel>(viewModel, context) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemBiliFavoriteHolder(parent)
    }
}