package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliWatchLaterData
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliResourceRVViewModel

class WatchLaterViewModel : BiliResourceRVViewModel() {
    private val mBiliAccountRepository = NetworkManager.biliAccountRepository

    fun initData() {
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
        mBiliAccountRepository.requestWatchLater(
            object : IServerCallback<BiliWatchLaterData> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: BiliWatchLaterData
                ) {
                    val list = data.list.orEmpty().mapNotNullTo(mutableListOf<Any>()) {
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
