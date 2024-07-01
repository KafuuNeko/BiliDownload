package cc.kafuu.bilidownload.viewmodel.fragment

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.model.LoadingStatus

open class RVViewModel : CoreViewModel() {
    private val mHandler = Handler(Looper.getMainLooper())

    var listMutableLiveData: MutableLiveData<MutableList<Any>> = MutableLiveData<MutableList<Any>>(
        mutableListOf()
    )
    val loadingStatusMessageMutableLiveData = MutableLiveData(LoadingStatus.waitStatus())

    fun updateList(newList: MutableList<Any>) {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            mHandler.post { updateList(newList) }
            return
        }

        listMutableLiveData.value = newList
        loadingStatusMessageMutableLiveData.value =
            if (newList.isEmpty()) LoadingStatus.emptyStatus() else LoadingStatus.doneStatus()
    }

}