package cc.kafuu.bilidownload.viewmodel.activity

import android.util.Log
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.model.BiliSearchData

class SearchViewModel : CoreViewModel() {
    companion object {
        private const val TAG = "SearchViewModel"
    }

    val searchContextLiveData = MutableLiveData<String>()

    fun onSearch() {
        Log.d(TAG, "onSearch: ${searchContextLiveData.value}")
        NetworkManager.biliSearchRepository.search(searchContextLiveData.value!!, object :
            IServerCallback<BiliSearchData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliSearchData
            ) {
                Log.d(TAG, "onSuccess: $data")
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                Log.d(TAG, "onFailure: httpCode($httpCode), code($code), message($message)")
            }

        })
    }

}