package cc.kafuu.bilidownload.viewmodel.dialog

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.model.ConfirmDialogStatus
import cc.kafuu.bilidownload.model.ResourceType
import cc.kafuu.bilidownload.model.bili.BiliStreamResourceModel

typealias BiliPartDialogCallback = (video: BiliPlayStreamResource?, audio: BiliPlayStreamResource?) -> Unit

class BiliPartViewModel : CoreViewModel() {
    val dialogStatusLiveData = MutableLiveData(ConfirmDialogStatus.WAITING)

    val titleLiveData = MutableLiveData<String>()

    val videoResourcesLiveData = MutableLiveData<List<BiliStreamResourceModel>>(listOf())
    val audioResourcesLiveData = MutableLiveData<List<BiliStreamResourceModel>>(listOf())

    val currentVideoResourceLiveData = MutableLiveData<BiliStreamResourceModel>(null)
    val currentAudioResourceLiveData = MutableLiveData<BiliStreamResourceModel>(null)

    var confirmCallback: BiliPartDialogCallback? = null

    fun onConfirm() {
        dialogStatusLiveData.value = ConfirmDialogStatus.CONFIRMING
    }

    fun onClose() {
        dialogStatusLiveData.value = ConfirmDialogStatus.CLOSED
    }

    fun onSelected(item: BiliStreamResourceModel) {
        when (item.type) {
            ResourceType.VIDEO -> currentVideoResourceLiveData.value = item
            ResourceType.AUDIO -> currentAudioResourceLiveData.value = item
        }
    }

    fun isSelected(item: BiliStreamResourceModel) = when (item.type) {
        ResourceType.VIDEO -> currentVideoResourceLiveData.value == item
        ResourceType.AUDIO -> currentAudioResourceLiveData.value == item
        else -> false
    }
}