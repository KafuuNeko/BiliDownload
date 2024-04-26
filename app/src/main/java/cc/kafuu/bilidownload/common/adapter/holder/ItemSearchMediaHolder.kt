package cc.kafuu.bilidownload.common.adapter.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.databinding.ItemSearchMediaBinding

class ItemSearchMediaHolder(parent: ViewGroup) : CoreRVHolder<ItemSearchMediaBinding>(
    ItemSearchMediaBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
    )
) {
    override fun getDataVariableId(): Int = BR.data

    override fun getVMVariableId(): Int = BR.viewModel
}