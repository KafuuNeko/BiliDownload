package cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource

import cc.kafuu.bilidownload.common.manager.DownloadSourceConfig

sealed class DownloadSourceUiIntent {
    data object Init : DownloadSourceUiIntent()
    data object TryBack : DownloadSourceUiIntent()
    data class SelectSource(val source: DownloadSourceConfig.DownloadSource) : DownloadSourceUiIntent()
}
