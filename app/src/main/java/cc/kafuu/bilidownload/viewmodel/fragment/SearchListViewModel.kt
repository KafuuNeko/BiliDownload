package cc.kafuu.bilidownload.viewmodel.fragment

import android.util.Log
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.network.model.BiliSearchMediaResultData
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
import cc.kafuu.bilidownload.model.BiliMedia
import cc.kafuu.bilidownload.model.BiliVideo
import cc.kafuu.bilidownload.model.LoadingStatus
import cc.kafuu.bilidownload.model.SearchType
import com.bumptech.glide.load.resource.bitmap.CenterCrop

class SearchListViewModel : RVViewModel() {
    val centerCrop = CenterCrop()
    var keyword: String? = null
    @SearchType
    var searchType: Int = SearchType.VIDEO

    private var mNextPage = 1

    companion object {
        private const val TAG = "SearchListViewModel"
        private val mBiliSearchRepository = NetworkManager.biliSearchRepository
    }

    fun doSearch(
        loadingStatus: LoadingStatus,
        loadMore: Boolean,
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        if (keyword == null || loadingStatusMessageMutableLiveData.value?.statusCode == LoadingStatus.CODE_LOADING) {
            if (keyword != null) Log.d(TAG, "doSearch: Search execution")
            return
        }
        if (!loadMore) mNextPage = 1
        loadingStatusMessageMutableLiveData.value = loadingStatus

        when (searchType) {
            SearchType.VIDEO -> mBiliSearchRepository.searchVideo(
                keyword!!, mNextPage,
                createSearchCallback(onSucceeded, onFailed, loadMore)
            )

            SearchType.MEDIA_BANGUMI -> mBiliSearchRepository.searchMediaBangumi(
                keyword!!, mNextPage,
                createSearchCallback(onSucceeded, onFailed, loadMore)
            )

            SearchType.MEDIA_FT -> mBiliSearchRepository.searchMediaFt(
                keyword!!, mNextPage,
                createSearchCallback(onSucceeded, onFailed, loadMore)
            )
        }
    }

    private fun <T> createSearchCallback(
        onSucceeded: (() -> Unit)?,
        onFailed: (() -> Unit)?,
        loadMore: Boolean
    ) = object : IServerCallback<BiliSearchData<T>> {
        override fun onSuccess(
            httpCode: Int,
            code: Int,
            message: String,
            data: BiliSearchData<T>
        ) {
            onSucceeded?.invoke()
            onLoadingCompleted(data, loadMore)
        }

        override fun onFailure(httpCode: Int, code: Int, message: String) {
            onFailed?.invoke()
            LoadingStatus.errorStatus(visibility = !loadMore, message = message).let {
                loadingStatusMessageMutableLiveData.postValue(it)
            }
        }
    }

    private fun onLoadingCompleted(data: BiliSearchData<*>, loadMore: Boolean) {
        Log.d(TAG, "onLoadingCompleted: $data")
        val searchData: MutableList<Any> = if (loadMore) {
            listMutableLiveData.value ?: mutableListOf()
        } else {
            mutableListOf()
        }
        searchData.addAll(data.result.orEmpty().map { result ->
            when (result) {
                is BiliSearchVideoResultData -> disposeResult(result)
                is BiliSearchMediaResultData -> disposeResult(result)
                else -> throw IllegalStateException("Unknown result from $result")
            }
        })
        loadingStatusMessageMutableLiveData.postValue(
            if (searchData.isEmpty()) LoadingStatus.emptyStatus() else LoadingStatus.doneStatus()
        )
        mNextPage++
        listMutableLiveData.postValue(searchData)
    }

    private fun disposeResult(element: BiliSearchVideoResultData) = BiliVideo(
        author = element.author,
        authorId = element.mid,
        bvid = element.bvid,
        title = element.title,
        description = element.description,
        pic = "https:${element.pic}",
        pubDate = element.pubDate,
        sendDate = element.sendDate,
        duration = element.duration
    )

    private fun disposeResult(element: BiliSearchMediaResultData) = BiliMedia(
        mediaId = element.mediaId,
        seasonId = element.seasonId,
        title = element.title,
        cover = element.cover,
        mediaType = element.mediaType,
        desc = element.desc
    )
}