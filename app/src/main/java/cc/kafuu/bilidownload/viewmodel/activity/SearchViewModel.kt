package cc.kafuu.bilidownload.viewmodel.activity

import android.util.Log
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel

class SearchViewModel : CoreViewModel() {
    companion object {
        private const val TAG = "SearchViewModel"
    }

    val searchContextLiveData = MutableLiveData<String>()
    val searchRequestLiveData = MutableLiveData<String>()

    fun onSearch() {
        Log.d(TAG, "onSearch: ${searchContextLiveData.value}")
        searchRequestLiveData.value = searchContextLiveData.value
    }

}