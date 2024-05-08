package cc.kafuu.bilidownload.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.model.BiliVideoPage
import cc.kafuu.bilidownload.model.BiliMedia
import cc.kafuu.bilidownload.model.BiliVideo
import cc.kafuu.bilidownload.model.LoadingStatus

class VideoDetailsViewModel : CoreViewModel() {
    val loadingStatusLiveData = MutableLiveData<LoadingStatus>()
    val biliVideoLiveData = MutableLiveData<BiliVideo>()
    val biliVideoPageListLiveData = MutableLiveData<List<BiliVideoPage>>()


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
}