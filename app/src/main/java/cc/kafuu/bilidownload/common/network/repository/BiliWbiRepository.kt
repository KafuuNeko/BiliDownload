package cc.kafuu.bilidownload.common.network.repository

import android.util.Log
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliWbiRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    companion object {
        private const val TAG = "BiliWbiRepository"

        private fun String.extractBetweenWbiAndDot(): String {
            return this.substringAfter("wbi/").substringBefore(".")
        }
    }

    fun syncGetWbiKey(onFailure: ((Int, Int, String) -> Unit)? = null): Pair<String, String>? {
        return biliApiService.getWbiInterfaceNav().execute(onFailure) {
            Pair(
                it.wbiImg.imgUrl.extractBetweenWbiAndDot(),
                it.wbiImg.subUrl.extractBetweenWbiAndDot()
            )
        }
    }
}