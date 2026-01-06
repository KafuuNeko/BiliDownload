package cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource

import cc.kafuu.bilidownload.common.manager.DownloadSourceConfig

sealed class DownloadSourceUiState {
    data object None : DownloadSourceUiState()

    data class Normal(
        val currentSource: DownloadSourceConfig.DownloadSource = DownloadSourceConfig.DownloadSource.AUTO,
        val sourceList: List<DownloadSourceConfig.DownloadSource> = DownloadSourceConfig.DownloadSource.entries.toList()
    ) : DownloadSourceUiState()

    data object Finished : DownloadSourceUiState()
}
