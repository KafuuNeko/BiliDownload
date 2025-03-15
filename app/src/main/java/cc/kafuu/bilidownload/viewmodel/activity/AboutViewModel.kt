package cc.kafuu.bilidownload.viewmodel.activity

import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.network.NetworkConfig

class AboutViewModel: CoreViewModel() {

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
    
    fun jumpUserAgreement() {
        // TODO
    }
    
    fun jumpPrivacyPolicy() {
        // TODO
    }
}