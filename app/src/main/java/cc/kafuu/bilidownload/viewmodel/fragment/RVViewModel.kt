package cc.kafuu.bilidownload.viewmodel.fragment

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.model.LoadingMessage
import com.bumptech.glide.load.resource.bitmap.CenterCrop

open class RVViewModel : CoreViewModel() {
    val centerCrop = CenterCrop()

    var listMutableLiveData: MutableLiveData<MutableList<Any>> = MutableLiveData<MutableList<Any>>(
        mutableListOf()
    )
    val loadingMessageMutableLiveData = MutableLiveData(LoadingMessage())
}