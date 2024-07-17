package cc.kafuu.bilidownload.viewmodel.fragment

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.model.LoadingStatus

open class RVViewModel : CoreViewModel() {
    private val mHandler = Handler(Looper.getMainLooper())

    private val mListMutableLiveData: MutableLiveData<MutableList<Any>> =
        MutableLiveData<MutableList<Any>>(
            mutableListOf()
        )
    val listMutableLiveData = mListMutableLiveData.liveData()

    protected val mLoadingStatusMessageMutableLiveData = MutableLiveData(LoadingStatus.waitStatus())
    val loadingStatusMessageMutableLiveData = mLoadingStatusMessageMutableLiveData.liveData()

    fun updateList(newList: MutableList<Any>) {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            mHandler.post { updateList(newList) }
            return
        }

        mListMutableLiveData.value = newList
        mLoadingStatusMessageMutableLiveData.value =
            if (newList.isEmpty()) LoadingStatus.emptyStatus() else LoadingStatus.doneStatus()
    }

}