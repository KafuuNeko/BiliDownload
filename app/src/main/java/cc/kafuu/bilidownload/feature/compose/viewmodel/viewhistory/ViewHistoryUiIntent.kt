package cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory

sealed class ViewHistoryUiIntent {
    data object LoadHistory : ViewHistoryUiIntent()
    data object TryBack : ViewHistoryUiIntent()
    data class ClickHistoryItem(val bvid: String) : ViewHistoryUiIntent()
    data class DeleteHistoryItem(val bvid: String) : ViewHistoryUiIntent()
    data object ClearAllHistory : ViewHistoryUiIntent()
}
