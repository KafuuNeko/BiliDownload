package cc.kafuu.bilidownload.viewmodel.fragment

import android.util.Log
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.model.LoadingStatus

class SearchListViewModel : RVViewModel() {
    companion object {
        private const val TAG = "SearchListViewModel"
    }

    fun doSearch(keyword: String) {
        if (loadingStatusMessageMutableLiveData.value == LOADING_STATUS_LOADING) {
            return
        }
        loadingStatusMessageMutableLiveData.value = LOADING_STATUS_LOADING
        NetworkManager.biliSearchRepository.search(
            keyword,
            object : IServerCallback<BiliSearchData> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: BiliSearchData
                ) {
                    onSearchRespond(data)
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    Log.d(TAG, "onFailure: httpCode($httpCode), code($code), message($message)")
                    loadingStatusMessageMutableLiveData.value =
                        LoadingStatus(true, CommonLibs.getDrawable(R.drawable.ic_error), message)
                }
            })
    }

    private fun onSearchRespond(data: BiliSearchData) {
        Log.d(TAG, "onSearchRespond: $data")
    }
}