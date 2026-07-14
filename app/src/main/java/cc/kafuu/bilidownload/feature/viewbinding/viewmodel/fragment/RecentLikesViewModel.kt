package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliLikeListData
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliRVViewModel

class RecentLikesViewModel : BiliRVViewModel() {
    private val mBiliAccountRepository = NetworkManager.biliAccountRepository
    private var mMid = 0L

    fun initData(mid: Long) {
        mMid = mid
        loadData(LoadingStatus.loadingStatus())
    }

    override fun onRefreshData(
        onSucceeded: (() -> Unit)?,
        onFailed: (() -> Unit)?
    ) {
        loadData(LoadingStatus.loadingStatus(false), onSucceeded, onFailed)
    }

    private fun loadData(
        loadingStatus: LoadingStatus,
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        setLoadingStatus(loadingStatus)
        mBiliAccountRepository.requestUserRecentLikes(
            mid = mMid,
            callback = object : IServerCallback<BiliLikeListData> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: BiliLikeListData
                ) {
                    val list = data.list.orEmpty().mapTo(mutableListOf<Any>()) {
                        BiliVideoModel.create(it)
                    }
                    updateList(list)
                    onSucceeded?.invoke()
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    setLoadingStatus(LoadingStatus.errorStatus(message = message))
                    onFailed?.invoke()
                }
            }
        )
    }
}
