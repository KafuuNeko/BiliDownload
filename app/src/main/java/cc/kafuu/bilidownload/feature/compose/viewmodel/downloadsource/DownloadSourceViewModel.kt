package cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource

import cc.kafuu.bilidownload.common.core.compose.CoreCompViewModelWithEvent
import cc.kafuu.bilidownload.common.core.compose.UiIntentObserver
import cc.kafuu.bilidownload.common.manager.DownloadSourceConfig

class DownloadSourceViewModel :
    CoreCompViewModelWithEvent<DownloadSourceUiIntent, DownloadSourceUiState, DownloadSourceUiEvent>(
        DownloadSourceUiState.None
    ) {

    /**
     * 初始化
     */
    @UiIntentObserver(DownloadSourceUiIntent.Init::class)
    fun onInit() {
        if (!isStateOf<DownloadSourceUiState.None>()) return
        val currentSource = DownloadSourceConfig.downloadSourceLiveData.value
            ?: DownloadSourceConfig.DownloadSource.AUTO
        DownloadSourceUiState.Normal(
            currentSource = currentSource,
            sourceList = DownloadSourceConfig.DownloadSource.entries.toList()
        ).setup()
    }

    /**
     * 选择下载源
     */
    @UiIntentObserver(DownloadSourceUiIntent.SelectSource::class)
    fun onSelectSource(intent: DownloadSourceUiIntent.SelectSource) {
        DownloadSourceConfig.setDownloadSource(intent.source)
        getOrNull<DownloadSourceUiState.Normal>()
            ?.copy(currentSource = intent.source)
            ?.setup()
    }

    /**
     * 返回
     */
    @UiIntentObserver(DownloadSourceUiIntent.TryBack::class)
    fun onTryBack() {
        DownloadSourceUiState.Finished.setup()
    }
}
