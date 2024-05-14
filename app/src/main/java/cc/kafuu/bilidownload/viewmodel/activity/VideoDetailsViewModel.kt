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
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliResourceModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.model.popmessage.ToastMessage

class VideoDetailsViewModel : CoreViewModel() {
    val loadingStatusLiveData = MutableLiveData(LoadingStatus.waitStatus())
    val biliResourceModelLiveData = MutableLiveData<BiliResourceModel>()
    val biliVideoPageListLiveData = MutableLiveData<List<BiliVideoPartModel>>()
    // 选中的片段
    val selectedVideoPartLiveData = MutableLiveData<BiliVideoPartModel?>()
    // 选中的片段视频流数据
    val selectedBiliPlayStreamDashLiveData = MutableLiveData<BiliPlayStreamDash>()
    // 正在加载视频流数据的片段
    val loadingVideoPartLiveData = MutableLiveData<BiliVideoPartModel?>()

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
                        bvid = it.bvid,
                        cid = it.cid,
                        name = "${it.title} ${it.longTitle}",
                        remark = it.badge
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
            NetworkManager.biliVideoRepository.requestSeasonDetailBySeasonId(
                media.seasonId,
                callback
            )
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
                        bvid = video.bvid,
                        cid = it.cid,
                        name = it.part,
                        remark = TimeUtils.formatSecondTime(it.duration)
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

    @Synchronized
    fun onPartSelected(item: BiliVideoPartModel) {
        if (loadingVideoPartLiveData.value != null) return

        selectedVideoPartLiveData.value = item
        loadingVideoPartLiveData.value = item

        val callback = object : IServerCallback<BiliPlayStreamDash> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliPlayStreamDash
            ) {
                selectedBiliPlayStreamDashLiveData.postValue(data)
                loadingVideoPartLiveData.postValue(null)
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                popMessage(ToastMessage(message, Toast.LENGTH_SHORT))
                loadingVideoPartLiveData.postValue(null)
            }
        }
        NetworkManager.biliVideoRepository.requestPlayStreamDash(item.bvid, item.cid, callback)
    }
}