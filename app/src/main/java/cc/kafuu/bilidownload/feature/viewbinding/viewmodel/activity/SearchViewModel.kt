package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity

import android.util.Log
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.viewbinding.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData

class SearchViewModel : CoreViewModel() {
    companion object {
        private const val TAG = "SearchViewModel"
    }

    private val mSearchRequestLiveData = MutableLiveData<String>()
    val searchRequestLiveData = mSearchRequestLiveData.liveData()

    fun onSearch(searchText: String) {
        Log.d(TAG, "onSearch: $searchText")
        mSearchRequestLiveData.value = searchText
    }

}