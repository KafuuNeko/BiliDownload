package cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory

import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel

sealed class ViewHistoryUiEvent {
    data class NavigateToVideoDetail(val video: BiliVideoModel) : ViewHistoryUiEvent()
}
