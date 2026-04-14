package cc.kafuu.bilidownload.feature.compose.viewmodel.settings

sealed class SettingsUiState {
    data object Loading : SettingsUiState()

    data class Normal(
        val downloadPathMode: Int,
        val currentPathDisplay: String,
    ) : SettingsUiState()
}
