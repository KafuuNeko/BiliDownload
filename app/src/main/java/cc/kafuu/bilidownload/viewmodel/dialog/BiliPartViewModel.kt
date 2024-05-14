package cc.kafuu.bilidownload.viewmodel.dialog

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.model.ConfirmDialogStatus
import cc.kafuu.bilidownload.common.model.DashType
import cc.kafuu.bilidownload.common.model.bili.BiliStreamResourceModel

typealias BiliPartDialogCallback = (video: BiliPlayStreamResource?, audio: BiliPlayStreamResource?) -> Unit

class BiliPartViewModel : CoreViewModel() {
    val dialogStatusLiveData = MutableLiveData(cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.WAITING)

    val titleLiveData = MutableLiveData<String>()

    val videoResourcesLiveData = MutableLiveData<List<BiliStreamResourceModel>>(listOf())
    val audioResourcesLiveData = MutableLiveData<List<BiliStreamResourceModel>>(listOf())

    val currentVideoResourceLiveData = MutableLiveData<BiliStreamResourceModel?>(null)
    val currentAudioResourceLiveData = MutableLiveData<BiliStreamResourceModel?>(null)

    val previousResourceLiveData = MutableLiveData<BiliStreamResourceModel?>(null)

    val confirmTextLiveData =
        MutableLiveData(CommonLibs.getString(R.string.text_resource_not_selected))

    var confirmCallback: BiliPartDialogCallback? = null

    fun onConfirm() {
        dialogStatusLiveData.value = cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.CONFIRMING
    }

    fun onClose() {
        dialogStatusLiveData.value = cc.kafuu.bilidownload.common.model.ConfirmDialogStatus.CLOSED
    }

    fun onSelected(item: BiliStreamResourceModel) {
        var previousResource: BiliStreamResourceModel? = null
        when (item.type) {
            DashType.VIDEO -> {
                previousResource = currentVideoResourceLiveData.value
                currentVideoResourceLiveData.value =
                    if (currentVideoResourceLiveData.value == item) null else item
            }

            DashType.AUDIO -> {
                previousResource = currentAudioResourceLiveData.value
                currentAudioResourceLiveData.value =
                    if (currentAudioResourceLiveData.value == item) null else item
            }
        }
        previousResourceLiveData.value = previousResource
    }

    fun isSelected(item: BiliStreamResourceModel) = when (item.type) {
        DashType.VIDEO -> currentVideoResourceLiveData.value == item
        DashType.AUDIO -> currentAudioResourceLiveData.value == item
        else -> false
    }
}