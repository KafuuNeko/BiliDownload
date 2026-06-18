package cc.kafuu.bilidownload.feature.compose.viewmodel.settings

import cc.kafuu.bilidownload.common.model.DownloadPathMode

sealed class SettingsUiEvent {
    data object Finish : SettingsUiEvent()
    data class RequestPermission(val mode: DownloadPathMode) : SettingsUiEvent()
    data object PermissionDenied : SettingsUiEvent()
}
