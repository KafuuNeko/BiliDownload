package cc.kafuu.bilidownload.viewmodel.fragment

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.model.LoadingStatus

open class RVViewModel : CoreViewModel() {
    companion object {
        val LOADING_STATUS_DONE = LoadingStatus()
        val LOADING_STATUS_EMPTY = LoadingStatus(
            true,
            CommonLibs.getDrawable(R.drawable.ic_list_item_empty),
            CommonLibs.getString(R.string.list_is_empty)
        )
        val LOADING_STATUS_LOADING = LoadingStatus(
            loadAnimationVisible = true
        )
    }

    var listMutableLiveData: MutableLiveData<MutableList<Any>> = MutableLiveData<MutableList<Any>>(
        mutableListOf()
    )
    val loadingStatusMessageMutableLiveData = MutableLiveData(LOADING_STATUS_DONE)
}