package cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory

import cc.kafuu.bilidownload.common.room.entity.ViewHistoryEntity

sealed class ViewHistoryUiState {
    data object Loading : ViewHistoryUiState()
    data class Loaded(
        val historyList: List<ViewHistoryEntity> = emptyList()
    ) : ViewHistoryUiState()
}
