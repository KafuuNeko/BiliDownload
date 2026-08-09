package cc.kafuu.bilidownload.feature.compose.viewmodel.settings

import android.os.Build
import android.os.Environment
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.compose.CoreCompViewModelWithEvent
import cc.kafuu.bilidownload.common.core.compose.UiIntentObserver
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.utils.LocalNetworkPermissionPolicy
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel :
    CoreCompViewModelWithEvent<SettingsUiIntent, SettingsUiState, SettingsUiEvent>(
        SettingsUiState.Loading
    ) {
    private var mLocalNetworkPermissionRequestSent = false
    private var mLocalNetworkPermissionCheckInProgress = false
    private var mFinishAfterLocalNetworkPermissionResult = false

    @UiIntentObserver(SettingsUiIntent.Init::class)
    fun onInit() {
        if (!isStateOf<SettingsUiState.Loading>()) return
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.SetDownloadPathMode::class)
    fun onSetDownloadPathMode(intent: SettingsUiIntent.SetDownloadPathMode) {
        if (intent.mode != DownloadPathMode.INTERNAL && needsStoragePermission()) {
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

    @UiIntentObserver(SettingsUiIntent.SetAutoRemuxAudioAfterDownload::class)
    fun onSetAutoRemuxAudioAfterDownload(
        intent: SettingsUiIntent.SetAutoRemuxAudioAfterDownload
    ) {
        AppModel.autoRemuxAudioAfterDownload = intent.enabled
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.SetDownloadSourceMode::class)
    fun onSetDownloadSourceMode(intent: SettingsUiIntent.SetDownloadSourceMode) {
        AppModel.downloadSourceMode = intent.mode
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.SetDownloadSourceCustomHost::class)
    fun onSetDownloadSourceCustomHost(intent: SettingsUiIntent.SetDownloadSourceCustomHost) {
        AppModel.downloadSourceCustomHost = intent.host.trim()
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.SetAudioResourceFileNameTemplate::class)
    fun onSetAudioResourceFileNameTemplate(
        intent: SettingsUiIntent.SetAudioResourceFileNameTemplate
    ) {
        AppModel.audioResourceFileNameTemplate = intent.template
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.SetVideoResourceFileNameTemplate::class)
    fun onSetVideoResourceFileNameTemplate(
        intent: SettingsUiIntent.SetVideoResourceFileNameTemplate
    ) {
        AppModel.videoResourceFileNameTemplate = intent.template
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.SetMixedResourceFileNameTemplate::class)
    fun onSetMixedResourceFileNameTemplate(
        intent: SettingsUiIntent.SetMixedResourceFileNameTemplate
    ) {
        AppModel.mixedResourceFileNameTemplate = intent.template
        refreshState()
    }

    @UiIntentObserver(SettingsUiIntent.GoBack::class)
    fun onGoBack() {
        requestLocalNetworkPermissionBeforeFinish()
    }

    /**
     * 权限授予后由 Activity 调用
     */
    fun onPermissionGranted(mode: DownloadPathMode) {
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

    fun onLocalNetworkPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            mLocalNetworkPermissionCheckInProgress = false
            if (!granted) {
                SettingsUiEvent.LocalNetworkPermissionDenied.send()
            }
            if (mFinishAfterLocalNetworkPermissionResult) {
                mFinishAfterLocalNetworkPermissionResult = false
                SettingsUiEvent.Finish.send()
            }
        }
    }

    private fun applyDownloadPathMode(mode: DownloadPathMode) {
        AppModel.downloadPathMode = mode
        refreshState()
    }

    private fun requestLocalNetworkPermissionBeforeFinish() {
        if (mLocalNetworkPermissionCheckInProgress) return
        mLocalNetworkPermissionCheckInProgress = true
        mFinishAfterLocalNetworkPermissionResult = true
        val sourceMode = AppModel.downloadSourceMode
        val host = AppModel.downloadSourceCustomHost
        viewModelScope.launch {
            val shouldRequest = LocalNetworkPermissionPolicy.shouldRequest(
                sdkInt = Build.VERSION.SDK_INT,
                sourceMode = sourceMode,
                rawHost = host,
                requestSent = mLocalNetworkPermissionRequestSent
            )
            if (!shouldRequest) {
                mLocalNetworkPermissionCheckInProgress = false
                mFinishAfterLocalNetworkPermissionResult = false
                SettingsUiEvent.Finish.send()
                return@launch
            }
            mLocalNetworkPermissionRequestSent = true
            SettingsUiEvent.RequestLocalNetworkPermission.send()
        }
    }

    private fun refreshState() {
        val mode = AppModel.downloadPathMode
        val path = getDisplayPath(mode)
        SettingsUiState.Normal(
            downloadPathMode = mode,
            currentPathDisplay = path,
            downloadSourceMode = AppModel.downloadSourceMode,
            downloadSourceCustomHost = AppModel.downloadSourceCustomHost,
            deleteSourceFilesAfterMerge = AppModel.deleteSourceFilesAfterMerge,
            autoRemuxAudioAfterDownload = AppModel.autoRemuxAudioAfterDownload,
            audioResourceFileNameTemplate = AppModel.audioResourceFileNameTemplate,
            videoResourceFileNameTemplate = AppModel.videoResourceFileNameTemplate,
            mixedResourceFileNameTemplate = AppModel.mixedResourceFileNameTemplate,
        ).setup()
    }

    private fun getDisplayPath(mode: DownloadPathMode): String {
        return when (mode) {
            DownloadPathMode.EXTERNAL -> {
                val downloadDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                File(downloadDir, "BVD").absolutePath
            }
            DownloadPathMode.EXTERNAL_MEDIA -> {
                val moviesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES
                )
                File(moviesDir, "BVD").absolutePath
            }
            DownloadPathMode.INTERNAL -> {
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
