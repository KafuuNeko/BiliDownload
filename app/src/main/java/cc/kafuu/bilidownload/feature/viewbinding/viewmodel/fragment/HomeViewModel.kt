package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import cc.kafuu.bilidownload.common.core.viewbinding.CoreViewModel
import cc.kafuu.bilidownload.common.constant.MainTabType
import cc.kafuu.bilidownload.common.model.event.MainTabSwitchEvent
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.SearchActivity
import org.greenrobot.eventbus.EventBus

class HomeViewModel : CoreViewModel() {
    fun jumpSearchActivity() {
        startActivity(SearchActivity::class.java)
    }

    fun switchToMe() {
        EventBus.getDefault().post(MainTabSwitchEvent(MainTabType.TAB_ME))
    }

}