package cc.kafuu.bilidownload.feature.compose.viewmodel.settings

sealed class SettingsUiIntent {
    data object Init : SettingsUiIntent()
    data class SetDownloadPathMode(val mode: Int) : SettingsUiIntent()
    data object GoBack : SettingsUiIntent()
}
