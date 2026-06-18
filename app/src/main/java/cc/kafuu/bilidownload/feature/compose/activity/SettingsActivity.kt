package cc.kafuu.bilidownload.feature.compose.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.compose.CoreCompActivity
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.feature.compose.layout.SettingsLayout
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiEvent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsViewModel

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(SettingsUiIntent.Init)
    }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        SettingsLayout(uiState) { mViewModel.emit(it) }

        LaunchedEffect(Unit) {
            mViewModel.collectEvent { event ->
                when (event) {
                    SettingsUiEvent.Finish -> finish()

                    is SettingsUiEvent.RequestPermission -> {
                        onRequestStoragePermission(event.mode)
                    }

                    SettingsUiEvent.PermissionDenied -> {
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.settings_permission_denied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
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
}
