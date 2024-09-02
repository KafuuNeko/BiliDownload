package cc.kafuu.bilidownload.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliFavoriteModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteDetailsData
import cc.kafuu.bilidownload.viewmodel.common.BiliRVViewModel

class FavoriteDetailsViewModel : BiliRVViewModel() {
    companion object {
        const val PAGE_SIZE = 20
    }

    private val mBiliAccountRepository = NetworkManager.biliAccountRepository

    // 当前收藏夹信息
    private val mBiliFavoriteLiveData = MutableLiveData<BiliFavoriteModel>()
    val biliFavoriteLiveData = mBiliFavoriteLiveData.liveData()

    // 最近一次加载的页码
    private var mLatestPage = 0

    fun initData(favoriteModel: BiliFavoriteModel) {
        mBiliFavoriteLiveData.value = favoriteModel
    }

    fun loadData(
        loadingStatus: LoadingStatus,
        loadMore: Boolean,
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) {
        val biliFavorite = mBiliFavoriteLiveData.value ?: return

        if (!loadMore) {
            mLatestPage = 0
        }

        setLoadingStatus(loadingStatus)
        object : IServerCallback<BiliFavoriteDetailsData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliFavoriteDetailsData
            ) {
                onSucceeded?.invoke()
                onLoadingCompleted(data, loadMore)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                onFailed?.invoke()
                popMessage(ToastMessageAction(message))
            }

        }.also {
            mBiliAccountRepository.requestFavoriteDetails(
                id = biliFavorite.id, ps = PAGE_SIZE, pn = mLatestPage + 1, callback = it
            )
        }
    }

    private fun onLoadingCompleted(data: BiliFavoriteDetailsData, loadMore: Boolean) {
        val favoriteData: MutableList<Any> = if (loadMore) {
            listMutableLiveData.value ?: mutableListOf()
        } else {
            mutableListOf()
        }
        val medias = data.medias ?: run {
            updateList(favoriteData)
            return
        }
        favoriteData.addAll(medias.map { BiliVideoModel.create(it) })
        updateList(favoriteData)
        mLatestPage++
    }
}