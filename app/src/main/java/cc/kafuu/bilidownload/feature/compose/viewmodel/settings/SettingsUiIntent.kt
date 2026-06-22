package cc.kafuu.bilidownload.feature.compose.viewmodel.settings

import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.model.DownloadSourceMode

sealed class SettingsUiIntent {
    data object Init : SettingsUiIntent()
    data class SetDownloadPathMode(val mode: DownloadPathMode) : SettingsUiIntent()
    data class SetDownloadSourceMode(val mode: DownloadSourceMode) : SettingsUiIntent()
    data class SetDownloadSourceCustomHost(val host: String) : SettingsUiIntent()
    data class SetDeleteSourceFilesAfterMerge(val enabled: Boolean) : SettingsUiIntent()
    data class SetAutoRemuxAudioAfterDownload(val enabled: Boolean) : SettingsUiIntent()
    data object GoBack : SettingsUiIntent()
}
