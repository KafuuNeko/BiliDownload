package cc.kafuu.bilidownload.viewmodel.fragment

import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.view.activity.LoginActivity

class MeViewModel : CoreViewModel() {

    fun jumpLogin() {
        if (AccountManager.accountLiveData.value != null) {
            return
        }
        startActivity(LoginActivity::class.java)
    }
}