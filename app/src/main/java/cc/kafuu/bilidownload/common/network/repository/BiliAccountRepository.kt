package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliAccountRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    fun syncGetAccountData(mid: Long, onFailure: ((Int, Int, String) -> Unit)?): BiliAccountData? {
        val params = linkedMapOf<String, Any>(
            "mid" to mid
        )
        return biliApiService.getAccountData(
            NetworkConfig.buildFullUrl(
                "x/space/wbi/acc/info", WbiManager.syncGenerateSignature(params)
            )
        ).execute(onFailure) { it }
    }
}