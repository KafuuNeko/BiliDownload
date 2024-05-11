package cc.kafuu.bilidownload.viewmodel.activity

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.model.BiliVideoPage
import cc.kafuu.bilidownload.model.LoadingStatus
import cc.kafuu.bilidownload.model.bili.BiliMedia
import cc.kafuu.bilidownload.model.bili.BiliVideo
import cc.kafuu.bilidownload.model.popmessage.ToastMessage

class VideoDetailsViewModel : CoreViewModel() {
    val loadingStatusLiveData = MutableLiveData(LoadingStatus.waitStatus())
    val biliVideoLiveData = MutableLiveData<BiliVideo>()
    val biliVideoPageListLiveData = MutableLiveData<List<BiliVideoPage>>()

    val selectedBiliPlayStreamDashLiveData =
        MutableLiveData<Pair<BiliVideoPage, BiliPlayStreamDash>>()

    fun initData(media: BiliMedia) {
        loadingStatusLiveData.value = LoadingStatus.loadingStatus()
    }

    fun initData(video: BiliVideo) {
        loadingStatusLiveData.value = LoadingStatus.loadingStatus()
        biliVideoLiveData.value = video
        val callback = object : IServerCallback<BiliVideoData> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: BiliVideoData) {
                biliVideoPageListLiveData.postValue(data.pages)
                loadingStatusLiveData.value = LoadingStatus.doneStatus()
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                loadingStatusLiveData.value = LoadingStatus.errorStatus(
                    message = message
                )
            }
        }
        NetworkManager.biliVideoRepository.getVideoDetail(video.bvid, callback)
    }

    fun onPartSelected(item: BiliVideoPage) {
        val biliVideo = biliVideoLiveData.value!!
        val callback = object : IServerCallback<BiliPlayStreamDash> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliPlayStreamDash
            ) {
                selectedBiliPlayStreamDashLiveData.postValue(Pair(item, data))
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                popMessage(ToastMessage(message, Toast.LENGTH_SHORT))
            }
        }
        NetworkManager.biliVideoRepository.getPlayStreamDash(biliVideo.bvid, item.cid, callback)
    }
}