package cc.kafuu.bilidownload.viewmodel.dialog

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.model.bili.BiliStreamResourceModel
import cc.kafuu.bilidownload.common.constant.ConfirmDialogStatus
import cc.kafuu.bilidownload.common.utils.liveData

typealias BiliPartDialogCallback = (video: BiliPlayStreamResource?, audio: BiliPlayStreamResource?) -> Unit

class BiliPartViewModel : CoreViewModel() {
    private val mDialogStatusLiveData = MutableLiveData(ConfirmDialogStatus.WAITING)
    val dialogStatusLiveData = mDialogStatusLiveData.liveData()

    private val mTitleLiveData = MutableLiveData<String>()
    val titleLiveData = mTitleLiveData.liveData()

    private val mVideoResourcesLiveData = MutableLiveData<List<BiliStreamResourceModel>>(listOf())
    val videoResourcesLiveData = mVideoResourcesLiveData.liveData()

    private val mAudioResourcesLiveData = MutableLiveData<List<BiliStreamResourceModel>>(listOf())
    val audioResourcesLiveData = mAudioResourcesLiveData.liveData()

    private val mCurrentVideoResourceLiveData = MutableLiveData<BiliStreamResourceModel?>(null)
    val currentVideoResourceLiveData = mCurrentVideoResourceLiveData.liveData()

    private val mCurrentAudioResourceLiveData = MutableLiveData<BiliStreamResourceModel?>(null)
    val currentAudioResourceLiveData = mCurrentAudioResourceLiveData.liveData()

    private val mPreviousResourceLiveData = MutableLiveData<BiliStreamResourceModel?>(null)
    val previousResourceLiveData = mPreviousResourceLiveData.liveData()

    private val mConfirmTextLiveData = MutableLiveData(CommonLibs.getString(R.string.text_resource_not_selected))
    val confirmTextLiveData = mConfirmTextLiveData.liveData()

    var confirmCallback: BiliPartDialogCallback? = null

    fun onConfirm() {
        mDialogStatusLiveData.value = ConfirmDialogStatus.CONFIRMING
    }

    fun onClose() {
        mDialogStatusLiveData.value = ConfirmDialogStatus.CLOSED
    }

    fun onSelected(item: BiliStreamResourceModel) {
        var previousResource: BiliStreamResourceModel? = null
        when (item.type) {
            DashType.VIDEO -> {
                previousResource = mCurrentVideoResourceLiveData.value
                mCurrentVideoResourceLiveData.value =
                    if (currentVideoResourceLiveData.value == item) null else item
            }

            DashType.AUDIO -> {
                previousResource = mCurrentAudioResourceLiveData.value
                mCurrentAudioResourceLiveData.value =
                    if (mCurrentAudioResourceLiveData.value == item) null else item
            }
        }
        mPreviousResourceLiveData.value = previousResource
    }

    fun isSelected(item: BiliStreamResourceModel) = when (item.type) {
        DashType.VIDEO -> mCurrentVideoResourceLiveData.value == item
        DashType.AUDIO -> mCurrentAudioResourceLiveData.value == item
        else -> false
    }

    fun updateTitle(title: String) {
        mTitleLiveData.value = title
    }

    fun updateVideoResources(items: List<BiliStreamResourceModel>) {
        mVideoResourcesLiveData.value = items
    }

    fun updateAudioResources(items: List<BiliStreamResourceModel>) {
        mAudioResourcesLiveData.value = items
    }

    fun changeStatus(@ConfirmDialogStatus status: Int) {
        mDialogStatusLiveData.value = status
    }

    fun updateConfirmText(text: String) {
        mConfirmTextLiveData.value = text
    }
}