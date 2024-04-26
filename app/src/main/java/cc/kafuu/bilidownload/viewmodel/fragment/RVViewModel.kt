package cc.kafuu.bilidownload.viewmodel.fragment

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.model.LoadingStatus

open class RVViewModel : CoreViewModel() {
    var listMutableLiveData: MutableLiveData<MutableList<Any>> = MutableLiveData<MutableList<Any>>(
        mutableListOf()
    )
    val loadingStatusMessageMutableLiveData = MutableLiveData(LoadingStatus.waitStatus())
}