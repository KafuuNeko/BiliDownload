package cc.kafuu.bilidownload.common.adapter.holder

import android.view.LayoutInflater
import androidx.core.view.isVisible
import cc.kafuu.bilidownload.common.model.bili.VideoStats
import android.view.ViewGroup
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVHolder
import cc.kafuu.bilidownload.databinding.ItemBiliVideoBinding

/** 普通视频卡片绑定器，统计区域独立刷新以支持列表复用。 */
class ItemBiliVideoHolder(parent: ViewGroup) : CoreRVHolder<ItemBiliVideoBinding>(
    ItemBiliVideoBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
    )
) {
    /** 只刷新统计区域；没有可用统计时保留简介，避免把未知值显示成零。 */
    fun bindStats(stats: VideoStats) {
        binding.videoStats.render(stats)
        binding.tvDescription.isVisible = !binding.videoStats.isVisible
    }

    override fun getDataVariableId(): Int = BR.data

    override fun getVMVariableId(): Int = BR.viewModel
}
