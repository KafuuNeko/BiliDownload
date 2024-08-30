package cc.kafuu.bilidownload.common.adapter.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.databinding.ItemBiliFavoriteBinding

class ItemBiliFavoriteHolder(parent: ViewGroup) : CoreRVHolder<ItemBiliFavoriteBinding>(
    ItemBiliFavoriteBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
    )
) {
    override fun getDataVariableId(): Int = BR.data

    override fun getVMVariableId(): Int = BR.viewModel
}