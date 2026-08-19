package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

/** 下载历史页面完整、可重放的多选 UI 状态。 */
data class HistoryMultiSelectUiState(
    val selectedIds: Set<Long> = emptySet(),
    val availableIds: Set<Long> = emptySet(),
) {
    val isEnabled: Boolean
        get() = selectedIds.isNotEmpty()

    val hasSelection: Boolean
        get() = selectedIds.isNotEmpty()

    val isAllSelected: Boolean
        get() = availableIds.isNotEmpty() && selectedIds == availableIds

    fun updateAvailableIds(ids: Set<Long>): HistoryMultiSelectUiState = copy(
        selectedIds = selectedIds intersect ids,
        availableIds = ids,
    )

    fun toggleItem(taskId: Long): HistoryMultiSelectUiState {
        if (taskId !in availableIds) return this
        val updatedIds = if (taskId in selectedIds) {
            selectedIds - taskId
        } else {
            selectedIds + taskId
        }
        return copy(selectedIds = updatedIds)
    }

    fun toggleAll(): HistoryMultiSelectUiState = copy(
        selectedIds = if (isAllSelected) emptySet() else availableIds,
    )

    fun clearSelection(): HistoryMultiSelectUiState = copy(selectedIds = emptySet())
}
