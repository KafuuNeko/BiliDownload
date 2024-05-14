package cc.kafuu.bilidownload.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.model.MainTabType

class MainViewModel: CoreViewModel() {
    val tabPositionLiveData = MutableLiveData(MainTabType.TAB_HOME)

    fun doChangeTabPosition(@MainTabType position: Int) {
        tabPositionLiveData.value = position
    }
}