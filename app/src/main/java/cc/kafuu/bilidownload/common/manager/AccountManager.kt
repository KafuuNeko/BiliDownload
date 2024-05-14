package cc.kafuu.bilidownload.common.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.model.MyBiliAccountData
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.utils.NetworkUtils
import cc.kafuu.bilidownload.common.model.bili.BiliAccountModel
import com.google.gson.JsonObject

object AccountManager {
    private const val TAG = "AccountManager"

    private const val FILE_CACHE = "account"
    private const val KEY_COOKIES = "cookies"

    val cookiesLiveData = MutableLiveData<String?>(null)
    val accountLiveData = MutableLiveData<BiliAccountModel?>(null)

    fun updateCookie(cookies: String? = null) {
        cookiesLiveData.value = cookies ?: getCookieLocalCache() ?: return
        Log.d(TAG, "updateCookie: ${cookiesLiveData.value}")
        val callback = object : IServerCallback<MyBiliAccountData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: MyBiliAccountData
            ) {
                Log.d(TAG, "updateCookie onSuccess: $data")
                requestAccountFace(data.mid)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                clearAccount()
                Log.e(TAG, "onFailure: httpCode=$httpCode, code=$code, message=$message")
            }
        }
        NetworkManager.biliAccountRepository.requestMyAccountData(callback)
    }

    fun logout() {
        val callback = object : IServerCallback<JsonObject> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: JsonObject
            ) {
                clearAccount(true)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                Log.e(TAG, "onFailure: httpCode=$httpCode, code=$code, message=$message")
            }
        }
        NetworkUtils.parseCookies(cookiesLiveData.value)["bili_jct"]?.let {
            NetworkManager.biliAccountRepository.requestLogout(it, callback)
        }
    }

    private fun requestAccountFace(mid: Long) {
        val callback = object : IServerCallback<BiliAccountData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliAccountData
            ) {
                Log.d(TAG, "requestAccountFace onSuccess: $data")
                updateCookieLocalCache()
                accountLiveData.postValue(
                    BiliAccountModel(
                        mid = data.mid,
                        nickname = data.name,
                        profile = data.avatarUrl,
                        sign = data.sign
                    )
                )
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                clearAccount()
                Log.e(TAG, "onFailure: httpCode=$httpCode, code=$code, message=$message")
            }

        }
        NetworkManager.biliAccountRepository.requestAccountData(mid, callback)
    }

    private fun clearAccount(removeLocalCache: Boolean = false) {
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