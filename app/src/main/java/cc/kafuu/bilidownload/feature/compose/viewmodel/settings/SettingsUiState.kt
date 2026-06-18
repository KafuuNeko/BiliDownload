package cc.kafuu.bilidownload.feature.compose.viewmodel.settings

import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.model.DownloadSourceMode

sealed class SettingsUiState {
    data object Loading : SettingsUiState()

    data class Normal(
        val downloadPathMode: DownloadPathMode,
        val currentPathDisplay: String,
        val downloadSourceMode: DownloadSourceMode,
        val downloadSourceCustomHost: String,
        val deleteSourceFilesAfterMerge: Boolean,
    ) : SettingsUiState()
}
