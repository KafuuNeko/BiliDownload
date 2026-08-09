package cc.kafuu.bilidownload.feature.compose.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.compose.CoreCompActivity
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.utils.LocalNetworkPermissionPolicy
import cc.kafuu.bilidownload.common.utils.LocalNetworkPermissionRequestSession
import cc.kafuu.bilidownload.feature.compose.layout.SettingsLayout
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiEvent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

class SettingsActivity : CoreCompActivity() {
    private val mViewModel by viewModels<SettingsViewModel>()

    private var mPendingMode: DownloadPathMode? = null

    private val mPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingMode = mPendingMode ?: return@registerForActivityResult
        if (granted) {
            mViewModel.onPermissionGranted(pendingMode)
        } else {
            mViewModel.onPermissionDenied()
        }
        mPendingMode = null
    }

    private val mLocalNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        LocalNetworkPermissionRequestSession.complete(granted)
        mViewModel.onLocalNetworkPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            mViewModel.emit(SettingsUiIntent.GoBack)
        }
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mViewModel.collectEvent(::onUiEvent)
        }
        mViewModel.emit(SettingsUiIntent.Init)
    }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        SettingsLayout(uiState) { mViewModel.emit(it) }
    }

    private fun onUiEvent(event: SettingsUiEvent) {
        when (event) {
            SettingsUiEvent.Finish -> finish()

            is SettingsUiEvent.RequestPermission -> {
                onRequestStoragePermission(event.mode)
            }

            SettingsUiEvent.RequestLocalNetworkPermission -> {
                onRequestLocalNetworkPermission()
            }

            SettingsUiEvent.PermissionDenied -> {
                Toast.makeText(
                    this,
                    getString(R.string.settings_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }

            SettingsUiEvent.LocalNetworkPermissionDenied -> {
                Toast.makeText(
                    this,
                    getString(R.string.settings_local_network_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun onRequestStoragePermission(mode: DownloadPathMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 不需要额外权限
            mViewModel.onPermissionGranted(mode)
            return
        }

        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            mViewModel.onPermissionGranted(mode)
        } else {
            mPendingMode = mode
            mPermissionLauncher.launch(permission)
        }
    }

    private fun onRequestLocalNetworkPermission() {
        if (Build.VERSION.SDK_INT < LocalNetworkPermissionPolicy.ANDROID_17_API_LEVEL) {
            mViewModel.onLocalNetworkPermissionResult(true)
            return
        }

        val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            LocalNetworkPermissionRequestSession.complete(true)
            mViewModel.onLocalNetworkPermissionResult(true)
        } else {
            mLocalNetworkPermissionLauncher.launch(permission)
        }
    }
}
