package cc.kafuu.bilidownload.viewmodel.fragment

import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.view.activity.LoginActivity

class MeViewModel : CoreViewModel() {

    fun jumpLogin() {
        if (AccountManager.accountLiveData.value != null) {
            return
        }
        startActivity(LoginActivity::class.java)
    }

    fun jumpSourceRepository() {
        CommonLibs.jumpToUrl(NetworkConfig.SOURCE_REPOSITORY_URL)
    }

    fun jumpOpenSourceLicenses() {
        CommonLibs.jumpToUrl(NetworkConfig.OPEN_SOURCE_LICENSES_URL)
    }

    fun jumpFeedback() {
        CommonLibs.jumpToUrl(NetworkConfig.FEEDBACK_URL)
    }

    fun jumpGooglePlay() {
        CommonLibs.jumpToUrl(NetworkConfig.GOOGLE_PLAY_URL)
    }
}