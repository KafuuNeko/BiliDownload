package cc.kafuu.bilidownload.viewmodel.fragment

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.model.ActivityJumpData
import cc.kafuu.bilidownload.model.LoadingMessage

open class RVViewModel: CoreViewModel() {
    val listMutableLiveData = MutableLiveData<List<Any>>(listOf())
    val loadingMessageMutableLiveData = MutableLiveData(LoadingMessage())
}