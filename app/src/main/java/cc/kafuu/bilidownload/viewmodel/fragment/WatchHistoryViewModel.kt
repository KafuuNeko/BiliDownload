package cc.kafuu.bilidownload.viewmodel.fragment

import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliHistoryCursor
import cc.kafuu.bilidownload.common.network.model.BiliHistoryData
import cc.kafuu.bilidownload.common.network.model.BiliHistoryItem
import cc.kafuu.bilidownload.viewmodel.common.BiliRVViewModel

class WatchHistoryViewModel : BiliRVViewModel() {
    private val mBiliAccountRepository = NetworkManager.biliAccountRepository
    private var mLastHistoryCursor: BiliHistoryCursor? = null

    fun loadData(
        loadingStatus: LoadingStatus,
        loadMore: Boolean,
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) {
        setLoadingStatus(loadingStatus)
        val callback = object : IServerCallback<BiliHistoryData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliHistoryData
            ) {
                mLastHistoryCursor = data.cursor
                onLoadingCompleted(data.list, loadMore)
                onSucceeded?.invoke()
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                onFailed?.invoke()
                popMessage(ToastMessageAction(message))
            }
        }
        if (!loadMore) {
            mBiliAccountRepository.requestArchiveHistory(
                callback = callback
            )
        } else {
            val cursor = mLastHistoryCursor ?: run {
                mBiliAccountRepository.requestArchiveHistory(
                    callback = callback
                )
                return
            }
            mBiliAccountRepository.requestArchiveHistory(
                max = cursor.max,
                viewAt = cursor.viewAt,
                callback = callback
            )
        }
    }

    private fun onLoadingCompleted(data: List<BiliHistoryItem>, loadMore: Boolean) {
        val list: MutableList<Any> = if (loadMore) {
            listMutableLiveData.value ?: mutableListOf()
        } else {
            mutableListOf()
        }

        data.mapNotNull {
            if (it.history.epid != null && it.history.epid != 0L) {
                BiliMediaModel.create(it)
            } else if (it.history.bvid != null) {
                BiliVideoModel.create(it)
            } else null
        }.also {
            list.addAll(it)
        }

        updateList(list)
    }

}