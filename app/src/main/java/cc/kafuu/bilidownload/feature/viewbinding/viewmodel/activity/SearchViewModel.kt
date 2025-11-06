package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.common.constant.SearchType
import cc.kafuu.bilidownload.common.core.viewbinding.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.room.repository.SearchRecordRepository
import kotlinx.coroutines.launch

class SearchViewModel : CoreViewModel() {
    companion object {
        private const val TAG = "SearchViewModel"
    }

    private val mSearchRequestLiveData = MutableLiveData<String>()
    val searchRequestLiveData = mSearchRequestLiveData.liveData()

    val searchRecordLiveData = SearchRecordRepository.observe()

    fun onSearch(searchText: String, @SearchType searchType: Int) {
        Log.d(TAG, "onSearch: $searchText")
        mSearchRequestLiveData.value = searchText
        viewModelScope.launch {
            if (searchText.isBlank()) return@launch
            SearchRecordRepository.add(searchText, searchType)
        }
    }

}