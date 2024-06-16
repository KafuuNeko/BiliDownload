package cc.kafuu.bilidownload.viewmodel.fragment

import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.view.activity.LoginActivity

class MeViewModel : CoreViewModel() {

    fun jumpLogin() {
        if (AccountManager.accountLiveData.value != null) {
            return
        }
        startActivity(LoginActivity::class.java)
    }

    fun jumpSourceRepository() {
        CommonLibs.jumpToUrl("https://github.com/KafuuNeko/BiliDownload")
    }

    fun jumpOpenSourceLicenses() {
        CommonLibs.jumpToUrl("https://github.com/KafuuNeko/BiliDownload/blob/master/LICENSE")
    }

    fun jumpFeedback() {
        CommonLibs.jumpToUrl("https://github.com/KafuuNeko/BiliDownload/issues")
    }
}