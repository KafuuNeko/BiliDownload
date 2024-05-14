package cc.kafuu.bilidownload.viewmodel.fragment

import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.model.MainTabType
import cc.kafuu.bilidownload.common.model.event.MainTabSwitchEvent
import cc.kafuu.bilidownload.view.activity.SearchActivity
import org.greenrobot.eventbus.EventBus

class HomeViewModel : CoreViewModel() {
    fun jumpSearchActivity() {
        startActivity(SearchActivity::class.java)
    }

    fun switchToMe() {
        EventBus.getDefault().post(MainTabSwitchEvent(MainTabType.TAB_ME))
    }

}