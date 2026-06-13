package cc.kafuu.bilidownload.feature.compose.viewmodel.settings

import android.os.Build
import android.os.Environment
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.compose.CoreCompViewModelWithEvent
import cc.kafuu.bilidownload.common.core.compose.UiIntentObserver
import cc.kafuu.bilidownload.common.model.AppModel
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel :
    CoreCompViewModelWithEvent<SettingsUiIntent, SettingsUiState, SettingsUiEvent>(
        SettingsUiState.Loading
    ) {

    @UiIntentObserver(SettingsUiIntent.Init::class)
    fun onInit() {
        if (!isStateOf<SettingsUiState.Loading>()) return
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.SetDownloadPathMode::class)
    fun onSetDownloadPathMode(intent: SettingsUiIntent.SetDownloadPathMode) {
        if (intent.mode == AppModel.DOWNLOAD_PATH_EXTERNAL && needsStoragePermission()) {
            // 需要先请求权限
            viewModelScope.launch {
                SettingsUiEvent.RequestPermission(intent.mode).send()
            }
            return
        }
        applyDownloadPathMode(intent.mode)
    }

    @UiIntentObserver(SettingsUiIntent.SetDeleteSourceFilesAfterMerge::class)
    fun onSetDeleteSourceFilesAfterMerge(
        intent: SettingsUiIntent.SetDeleteSourceFilesAfterMerge
    ) {
        AppModel.deleteSourceFilesAfterMerge = intent.enabled
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.GoBack::class)
    fun onGoBack() = viewModelScope.launch {
        SettingsUiEvent.Finish.send()
    }

    /**
     * 权限授予后由 Activity 调用
     */
    fun onPermissionGranted(mode: Int) {
        applyDownloadPathMode(mode)
    }

    /**
     * 权限拒绝后由 Activity 调用
     */
    fun onPermissionDenied() {
        viewModelScope.launch {
            SettingsUiEvent.PermissionDenied.send()
        }
    }

    private fun applyDownloadPathMode(mode: Int) {
        AppModel.downloadPathMode = mode
        refreshState()
    }

    private fun refreshState() {
        val mode = AppModel.downloadPathMode
        val path = getDisplayPath(mode)
        SettingsUiState.Normal(
            downloadPathMode = mode,
            currentPathDisplay = path,
            deleteSourceFilesAfterMerge = AppModel.deleteSourceFilesAfterMerge,
        ).setup()
    }

    private fun getDisplayPath(mode: Int): String {
        return when (mode) {
            AppModel.DOWNLOAD_PATH_EXTERNAL -> {
                val downloadDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                File(downloadDir, "BVD").absolutePath
            }
            else -> {
                try {
                    CommonLibs.requireContext().getExternalFilesDir("resources")?.absolutePath
                        ?: "N/A"
                } catch (_: Exception) {
                    "N/A"
                }
            }
        }
    }

    private fun needsStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }
}
