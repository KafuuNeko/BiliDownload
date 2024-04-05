package cc.kafuu.bilidownload.common.network.repository

import android.util.Log
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliWbiRepository(private val biliApiService: BiliApiService) : BiliRepository() {
    companion object {
        private const val TAG = "BiliWbiRepository"
    }

    fun syncGetWbiKey(onFailure: ((Int, Int, String) -> Unit)? = null): Pair<String, String>? {
        return biliApiService.getWbiInterfaceNav().execute(onFailure) {
            val wbiImg = it.wbiImg

            val imgKey = wbiImg.imgUrl.substringAfter("wbi/").substringBefore(".")
            val subKey = wbiImg.subUrl.substringAfter("wbi/").substringBefore(".")

            Pair(imgKey, subKey)
        }
    }
}