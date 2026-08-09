package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.common.model.DownloadSourceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Android 17 本地网络权限的触发策略。 */
object LocalNetworkPermissionPolicy {
    const val ANDROID_17_API_LEVEL = 37

    suspend fun shouldRequest(
        sdkInt: Int,
        sourceMode: DownloadSourceMode,
        rawHost: String,
        requestSent: Boolean
    ): Boolean {
        if (sdkInt < ANDROID_17_API_LEVEL ||
            sourceMode != DownloadSourceMode.CUSTOM_HOST ||
            requestSent
        ) {
            return false
        }
        return withContext(Dispatchers.IO) {
            LocalNetworkHostUtils.requiresPermission(rawHost)
        }
    }
}

/** 防止用户拒绝后在同一次进程会话的每个页面重复弹出权限请求。 */
object LocalNetworkPermissionRequestSession {
    enum class Action {
        REQUEST,
        AWAIT_RESULT,
        USE_DENIED_RESULT
    }

    private var mRequestStarted = false
    private val mResultFlow = MutableStateFlow<Boolean?>(null)

    @Synchronized
    fun nextAction(): Action {
        if (!mRequestStarted) {
            mRequestStarted = true
            return Action.REQUEST
        }

        return when (mResultFlow.value) {
            null -> Action.AWAIT_RESULT
            false -> Action.USE_DENIED_RESULT
            true -> {
                // 调用方已确认权限当前不再可用，说明授权随后被撤销，可以重新请求。
                mResultFlow.value = null
                Action.REQUEST
            }
        }
    }

    @Synchronized
    fun complete(granted: Boolean) {
        mRequestStarted = true
        mResultFlow.value = granted
    }

    suspend fun awaitResult(): Boolean = mResultFlow.filterNotNull().first()
}
