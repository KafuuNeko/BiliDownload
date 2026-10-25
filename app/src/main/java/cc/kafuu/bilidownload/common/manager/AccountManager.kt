package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.model.bili.BiliAccountModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.model.MyBiliAccountData
import cc.kafuu.bilidownload.common.utils.NetworkUtils
import com.google.gson.JsonObject

/**
 * 维护进程内账号状态与本地 Cookie 缓存，协调登录校验、账号资料刷新和退出登录。
 *
 * Cookie 仅交给网络层和私有偏好缓存，账号接口响应只用于构建页面模型，不输出完整凭据或资料。
 * 账号身份变化时先失效共享统计缓存，再通知观察者，避免页面继续复用旧身份的数据。
 */
object AccountManager {
    private const val TAG = "AccountManager"

    private const val FILE_CACHE = "account"
    private const val KEY_COOKIES = "cookies"

    /** 当前内存凭据；发布非空值早于账号校验完成，观察者不能据此认定登录成功。 */
    val cookiesLiveData = MutableLiveData<String?>(null)

    /** 已取得的账号展示模型；null 表示当前资料不可用，不单独证明服务端退出是否成功。 */
    val accountLiveData = MutableLiveData<BiliAccountModel?>(null)

    /**
     * 更新凭据前失效旧身份的统计请求，再发布 Cookie 状态并请求账号资料校验。
     *
     * 从主线程调用以更新 LiveData；Cookie 已发布不代表登录校验已经成功，账号模型另行通知。
     *
     * @param cookies 新凭据；为 null 时读取本地缓存，没有缓存则保持当前状态。
     */
    fun updateCookie(cookies: String? = null) {
        val nextCookies = cookies ?: getCookieLocalCache() ?: return
        if (cookiesLiveData.value != nextCookies) {
            NetworkManager.biliVideoStatsRepository.invalidate()
        }
        // 共享缓存已失效后才通知页面，页面据此取消旧订阅并重新读取可见稿件。
        cookiesLiveData.value = nextCookies
        val callback = object : IServerCallback<MyBiliAccountData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: MyBiliAccountData
            ) {
                requestAccountFace(mid = data.mid)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                clearAccount()
                Log.e(TAG, "onFailure: httpCode=$httpCode, code=$code, message=$message")
            }
        }
        NetworkManager.biliAccountRepository.requestMyAccountData(callback = callback)
    }

    /**
     * 使用当前 Cookie 中的 CSRF 字段请求服务端退出，成功后清理内存状态与本地缓存。
     *
     * 缺少该字段时不发出请求；服务端失败只记录错误，不提前将本地账号状态标记为退出。
     */
    fun logout() {
        val callback = object : IServerCallback<JsonObject> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: JsonObject
            ) {
                clearAccount(removeLocalCache = true)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                Log.e(TAG, "onFailure: httpCode=$httpCode, code=$code, message=$message")
            }
        }
        NetworkUtils.parseCookies(cookieHeader = cookiesLiveData.value)["bili_jct"]?.let {
            NetworkManager.biliAccountRepository.requestLogout(biliCSRF = it, callback = callback)
        }
    }

    /** 获取账号资料；请求成功后再缓存当前 Cookie 并发布账号模型，失败则清理内存账号状态。 */
    private fun requestAccountFace(mid: Long) {
        val callback = object : IServerCallback<BiliAccountData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliAccountData
            ) {
                updateCookieLocalCache()
                accountLiveData.postValue(BiliAccountModel.create(data = data))
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                clearAccount()
                Log.e(TAG, "onFailure: httpCode=$httpCode, code=$code, message=$message")
            }

        }
        NetworkManager.biliAccountRepository.requestAccountData(mid = mid, callback = callback)
    }

    /** 清理共享账号状态；默认保留持久缓存，只有明确退出成功时同时移除缓存。 */
    private fun clearAccount(removeLocalCache: Boolean = false) {
        // 先取消旧身份下的补齐，避免退出后的迟到响应重新填入共享缓存。
        NetworkManager.biliVideoStatsRepository.invalidate()
        cookiesLiveData.postValue(null)
        accountLiveData.postValue(null)
        if (removeLocalCache) {
            removeCookieLocalCache()
        }
    }

    private fun updateCookieLocalCache() {
        CommonLibs.requireContext().getSharedPreferences(FILE_CACHE, Context.MODE_PRIVATE).apply {
            edit().putString(KEY_COOKIES, cookiesLiveData.value).apply()
        }
    }

    private fun removeCookieLocalCache() {
        CommonLibs.requireContext().getSharedPreferences(FILE_CACHE, Context.MODE_PRIVATE).apply {
            edit().putString(KEY_COOKIES, null).apply()
        }
    }

    private fun getCookieLocalCache() = CommonLibs.requireContext()
        .getSharedPreferences(FILE_CACHE, Context.MODE_PRIVATE).getString(KEY_COOKIES, null)
}
