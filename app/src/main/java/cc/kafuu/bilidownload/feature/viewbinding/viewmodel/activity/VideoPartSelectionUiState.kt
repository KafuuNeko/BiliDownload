package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity

import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel

/** 分 P 多选状态；以稿件和 CID 组成稳定身份，刷新标题或时长不会丢失选择。 */
data class VideoPartSelectionUiState(
    val enabled: Boolean = false,
    val selectedIds: Set<Pair<String, Long>> = emptySet(),
    val availableIds: Set<Pair<String, Long>> = emptySet(),
) {
    val hasSelection: Boolean get() = selectedIds.isNotEmpty()
    val allSelected: Boolean get() = availableIds.isNotEmpty() && selectedIds == availableIds

    /** 判断当前分 P 是否被选中，不依赖对象实例或列表位置。 */
    fun isSelected(part: BiliVideoPartModel): Boolean = part.bvid to part.cid in selectedIds

    /** 刷新可选范围，同时移除已不在详情列表中的选择。 */
    fun updateParts(parts: List<BiliVideoPartModel>): VideoPartSelectionUiState {
        val ids = parts.mapTo(mutableSetOf()) { it.bvid to it.cid }
        return copy(availableIds = ids, selectedIds = selectedIds intersect ids)
    }

    /** 长按或点击分 P 时进入多选并切换该项；清空选择后仍可使用全部下载。 */
    fun toggle(part: BiliVideoPartModel): VideoPartSelectionUiState {
        val id = part.bvid to part.cid
        if (id !in availableIds) return this
        return copy(enabled = true, selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id)
    }

    /** 在全选与清空选择之间切换，保留多选操作栏。 */
    fun toggleAll(): VideoPartSelectionUiState = copy(
        enabled = true,
        selectedIds = if (allSelected) emptySet() else availableIds,
    )

    /** 退出多选，保留当前可选范围供下次长按使用。 */
    fun clear(): VideoPartSelectionUiState = copy(enabled = false, selectedIds = emptySet())
}
