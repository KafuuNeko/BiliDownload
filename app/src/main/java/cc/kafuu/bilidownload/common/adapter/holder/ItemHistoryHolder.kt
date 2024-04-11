package cc.kafuu.bilidownload.common.adapter.holder

import cc.kafuu.bilidownload.BR
import android.view.LayoutInflater
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.databinding.ItemHistoryBinding
import cc.kafuu.bilidownload.viewmodel.fragment.HistoryViewModel

class ItemHistoryHolder(parent: ViewGroup, private val mViewModel: HistoryViewModel) : CoreRVHolder<ItemHistoryBinding>(
    ItemHistoryBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
    )
) {
    override fun getDataVariableId(): Int = BR.data

    override fun getVMVariableId(): Int = BR.viewModel

    override fun onBinding(data: Any, position: Int) {
        val entity = data as DownloadTaskEntity

    }

}