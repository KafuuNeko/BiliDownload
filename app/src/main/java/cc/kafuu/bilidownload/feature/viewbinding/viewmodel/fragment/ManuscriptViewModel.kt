package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliSearchManuscriptData
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.FavoriteDetailsViewModel
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliRVViewModel

class ManuscriptViewModel : BiliRVViewModel() {
    companion object {
        const val PAGE_SIZE = 20
    }

    private val mBiliSearchRepository = NetworkManager.biliSearchRepository


    // 用户mid
    private var mMid: Long = 0

    // 最近一次加载的页码
    private var mLatestPage = 0

    fun initData(mid: Long) {
        mMid = mid
    }

    override fun onRefreshData(onSucceeded: (() -> Unit)?, onFailed: (() -> Unit)?) {
        loadData(
            loadingStatus = LoadingStatus.loadingStatus(false),
            loadMore = false,
            onSucceeded, onFailed
        )
    }

    override fun onLoadMoreData(onSucceeded: (() -> Unit)?, onFailed: (() -> Unit)?) {
        loadData(
            loadingStatus = LoadingStatus.loadingStatus(false),
            loadMore = true,
            onSucceeded, onFailed
        )
    }

    fun loadData(
        loadingStatus: LoadingStatus,
        loadMore: Boolean,
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) {
        if (!loadMore) {
            mLatestPage = 0
        }
        setLoadingStatus(loadingStatus)
        object : IServerCallback<BiliSearchManuscriptData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliSearchManuscriptData
            ) {
                onSucceeded?.invoke()
                onLoadingCompleted(data, loadMore)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                onFailed?.invoke()
                popMessage(ToastMessageAction(message))
            }

        }.also {
            mBiliSearchRepository.requestSearchManuscript(
                mid = mMid,
                ps = FavoriteDetailsViewModel.PAGE_SIZE, pn = mLatestPage + 1,
                callback = it
            )
        }
    }

    private fun onLoadingCompleted(data: BiliSearchManuscriptData, loadMore: Boolean) {
        val favoriteData: MutableList<Any> = if (loadMore) {
            listMutableLiveData.value ?: mutableListOf()
        } else {
            mutableListOf()
        }
        favoriteData.addAll(data.list.videoList.map {
            BiliVideoModel.create(it)
        })
        updateList(favoriteData)
        mLatestPage++
    }

}