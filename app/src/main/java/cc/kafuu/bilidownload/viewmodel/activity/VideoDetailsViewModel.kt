package cc.kafuu.bilidownload.viewmodel.activity

import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.utils.TimeUtils
import cc.kafuu.bilidownload.model.LoadingStatus
import cc.kafuu.bilidownload.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.model.bili.BiliResourceModel
import cc.kafuu.bilidownload.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.model.popmessage.ToastMessage

class VideoDetailsViewModel : CoreViewModel() {
    val loadingStatusLiveData = MutableLiveData(LoadingStatus.waitStatus())
    val biliResourceModelLiveData = MutableLiveData<BiliResourceModel>()
    val biliVideoPageListLiveData = MutableLiveData<List<BiliVideoPartModel>>()

    val selectedBiliPlayStreamDashLiveData =
        MutableLiveData<Pair<BiliVideoPartModel, BiliPlayStreamDash>>()

    fun initData(media: BiliMediaModel) {
        loadingStatusLiveData.value = LoadingStatus.loadingStatus()
        biliResourceModelLiveData.value = media

        val callback = object : IServerCallback<BiliSeasonData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliSeasonData
            ) {
                biliVideoPageListLiveData.postValue(data.episodes.map {
                    BiliVideoPartModel(
                        it.bvid,
                        it.cid,
                        "${it.title}: ${it.longTitle}",
                        null
                    )
                })
                loadingStatusLiveData.value = LoadingStatus.doneStatus()
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                loadingStatusLiveData.value = LoadingStatus.errorStatus(
                    message = message
                )
            }
        }
        if (media.seasonId != 0L) {
            NetworkManager.biliVideoRepository.requestSeasonDetailBySeasonId(media.seasonId, callback)
        } else {
            NetworkManager.biliVideoRepository.requestSeasonDetailByEpId(media.mediaId, callback)
        }
    }

    fun initData(video: BiliVideoModel) {
        loadingStatusLiveData.value = LoadingStatus.loadingStatus()
        biliResourceModelLiveData.value = video
        val callback = object : IServerCallback<BiliVideoData> {
            override fun onSuccess(httpCode: Int, code: Int, message: String, data: BiliVideoData) {
                biliVideoPageListLiveData.postValue(data.pages.map {
                    BiliVideoPartModel(
                        video.bvid,
                        it.cid,
                        it.part,
                        TimeUtils.formatSecondTime(it.duration)
                    )
                })
                loadingStatusLiveData.value = LoadingStatus.doneStatus()
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                loadingStatusLiveData.value = LoadingStatus.errorStatus(
                    message = message
                )
            }
        }
        NetworkManager.biliVideoRepository.requestVideoDetail(video.bvid, callback)
    }

    fun onPartSelected(item: BiliVideoPartModel) {
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
        NetworkManager.biliVideoRepository.requestPlayStreamDash(item.bvid, item.cid, callback)
    }
}