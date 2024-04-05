package cc.kafuu.bilidownload.common.network.repository

import android.util.Log
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliWbiRepository(private val biliApiService: BiliApiService) : BiliRepository() {
    companion object {
        private const val TAG = "BiliWbiRepository"
    }

    fun syncGetWbiKey(): Pair<String, String>? {
        return biliApiService.getWbiInterfaceNav().execute({ code, errorCode, errorMessage ->
            Log.e(
                TAG,
                "syncGetWbiKey: Error occurred. HTTP Status Code: $code, API Error Code: $errorCode, Message: $errorMessage"
            )
        }) {
            val wbiImg = it.wbiImg

            val imgKey = wbiImg.imgUrl.substringAfter("wbi/").substringBefore(".")
            val subKey = wbiImg.subUrl.substringAfter("wbi/").substringBefore(".")

            Pair(imgKey, subKey)
        }
    }
}