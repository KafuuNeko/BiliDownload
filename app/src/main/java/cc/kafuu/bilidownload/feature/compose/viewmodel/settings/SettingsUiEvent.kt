package cc.kafuu.bilidownload.feature.compose.viewmodel.settings

sealed class SettingsUiEvent {
    data object Finish : SettingsUiEvent()
    data class RequestPermission(val mode: Int) : SettingsUiEvent()
    data object PermissionDenied : SettingsUiEvent()
}
