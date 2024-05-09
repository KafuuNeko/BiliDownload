package cc.kafuu.bilidownload.viewmodel.dialog

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.model.ConfirmDialogStatus
import cc.kafuu.bilidownload.model.ResourceType
import cc.kafuu.bilidownload.model.bili.BiliResourceItem

class BiliPartViewModel : CoreViewModel() {
    val dialogStatusLiveData = MutableLiveData(ConfirmDialogStatus.WAITING)

    val titleLiveData = MutableLiveData<String>()

    val videoResourcesLiveData = MutableLiveData<List<BiliResourceItem>>(listOf())
    val audioResourcesLiveData = MutableLiveData<List<BiliResourceItem>>(listOf())

    val currentVideoResourceLiveData = MutableLiveData<BiliResourceItem>(null)
    val currentAudioResourceLiveData = MutableLiveData<BiliResourceItem>(null)

    fun onConfirm() {
        dialogStatusLiveData.value = ConfirmDialogStatus.CONFIRMING
    }

    fun onClose() {
        dialogStatusLiveData.value = ConfirmDialogStatus.CLOSED
    }

    fun onSelected(item: BiliResourceItem) {
        when (item.type) {
            ResourceType.VIDEO -> currentVideoResourceLiveData.value = item
            ResourceType.AUDIO -> currentAudioResourceLiveData.value = item
        }
    }

    fun isSelected(item: BiliResourceItem) = when (item.type) {
        ResourceType.VIDEO -> currentVideoResourceLiveData.value == item
        ResourceType.AUDIO -> currentAudioResourceLiveData.value == item
        else -> false
    }
}