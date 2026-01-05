package cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory

import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.compose.CoreCompViewModelWithEvent
import cc.kafuu.bilidownload.common.core.compose.UiIntentObserver
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.room.entity.ViewHistoryEntity
import cc.kafuu.bilidownload.common.room.repository.ViewHistoryRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ViewHistoryViewModel :
    CoreCompViewModelWithEvent<ViewHistoryUiIntent, ViewHistoryUiState, ViewHistoryUiEvent>(
        ViewHistoryUiState.Loading
    ) {
    private val viewHistoryRepository by lazy { ViewHistoryRepository(CommonLibs.requireContext()) }

    init {
        loadHistory()
    }

    @UiIntentObserver(ViewHistoryUiIntent.LoadHistory::class)
    fun onLoadHistory() {
        loadHistory()
    }

    @UiIntentObserver(ViewHistoryUiIntent.TryBack::class)
    fun onTryBack() {
        ViewHistoryUiState.Loaded().setup()
    }

    @UiIntentObserver(ViewHistoryUiIntent.ClickHistoryItem::class)
    fun onClickHistoryItem(intent: ViewHistoryUiIntent.ClickHistoryItem) = viewModelScope.launch {
        // 从历史记录中获取视频信息并构建 BiliVideoModel
        val history = viewHistoryRepository.getHistoryByBvid(intent.bvid)
        if (history != null) {
            val videoModel = BiliVideoModel(
                title = history.title,
                cover = history.cover,
                description = "",
                pubDate = history.viewTime,
                author = history.author,
                bvid = history.bvid,
                duration = ""
            )
            ViewHistoryUiEvent.NavigateToVideoDetail(videoModel).awaitSend()
        }
    }

    @UiIntentObserver(ViewHistoryUiIntent.DeleteHistoryItem::class)
    fun onDeleteHistoryItem(intent: ViewHistoryUiIntent.DeleteHistoryItem) = viewModelScope.launch {
        viewHistoryRepository.deleteByBvid(intent.bvid)
    }

    @UiIntentObserver(ViewHistoryUiIntent.ClearAllHistory::class)
    fun onClearAllHistory() = viewModelScope.launch {
        viewHistoryRepository.deleteAll()
    }

    private fun loadHistory() = viewModelScope.launch {
        viewHistoryRepository.getAllHistory()
            .catch { e ->
                e.printStackTrace()
                ViewHistoryUiState.Loaded(emptyList()).setup()
            }
            .collect { historyList ->
                ViewHistoryUiState.Loaded(historyList).setup()
            }
    }
}
