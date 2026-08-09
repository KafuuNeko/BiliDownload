package cc.kafuu.bilidownload.common.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import cc.kafuu.bilidownload.common.model.DownloadSourceMode
import cc.kafuu.bilidownload.common.utils.LocalNetworkPermissionRequestSession.Action
import kotlinx.coroutines.launch

/** 仅供可能访问自定义下载 Host 的页面执行 Android 17 局域网权限预检。 */
class LocalNetworkPermissionRequester(
    private val activity: ComponentActivity,
    private val onCompleted: (Boolean) -> Unit
) {
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        LocalNetworkPermissionRequestSession.complete(granted)
        onCompleted(granted)
    }

    fun check(sourceMode: DownloadSourceMode, rawHost: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (Build.VERSION.SDK_INT < LocalNetworkPermissionPolicy.ANDROID_17_API_LEVEL) {
            onCompleted(true)
            return
        }

        activity.lifecycleScope.launch {
            val shouldRequest = LocalNetworkPermissionPolicy.shouldRequest(
                sdkInt = Build.VERSION.SDK_INT,
                sourceMode = sourceMode,
                rawHost = rawHost,
                requestSent = false
            )
            if (!shouldRequest) {
                onCompleted(true)
                return@launch
            }

            val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
            if (ContextCompat.checkSelfPermission(activity, permission) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                LocalNetworkPermissionRequestSession.complete(true)
                onCompleted(true)
                return@launch
            }

            if (activity.isFinishing || activity.isDestroyed) return@launch
            when (LocalNetworkPermissionRequestSession.nextAction()) {
                Action.REQUEST -> permissionLauncher.launch(permission)
                Action.AWAIT_RESULT -> onCompleted(
                    LocalNetworkPermissionRequestSession.awaitResult()
                )
                Action.USE_DENIED_RESULT -> onCompleted(false)
            }
        }
    }
}
