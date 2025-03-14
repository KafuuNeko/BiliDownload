package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.constant.MainTabType
import cc.kafuu.bilidownload.common.core.viewbinding.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData

class MainViewModel : CoreViewModel() {
    private val mTabPositionLiveData = MutableLiveData(MainTabType.TAB_HOME)
    val tabPositionLiveData = mTabPositionLiveData.liveData()

    fun doChangeTabPosition(@MainTabType position: Int) {
        mTabPositionLiveData.value = position
    }
}