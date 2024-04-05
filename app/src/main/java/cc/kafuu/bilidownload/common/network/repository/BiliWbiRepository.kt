package cc.kafuu.bilidownload.common.network.repository

import android.util.Log
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliWbiRepository(private val biliApiService: BiliApiService) {
    companion object {
        private const val TAG = "BiliWbiRepository"
    }

    fun syncGetWbiKey(): Pair<String, String>? {
        try {
            val response = biliApiService.getWbiInterfaceNav().execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Network call failed: ${response.code()} - ${response.message()}")
                return null
            }

            val wbiImg = response.body()?.data?.wbiImg ?: run {
                Log.e(
                    TAG,
                    "Response body is null or data is missing. Error code: ${response.code()}, message: ${response.message()}"
                )
                return null
            }

            val imgKey = wbiImg.imgUrl.substringAfter("wbi/").substringBefore(".")
            val subKey = wbiImg.subUrl.substringAfter("wbi/").substringBefore(".")

            return Pair(imgKey, subKey)
        } catch (e: Exception) {
            Log.d(TAG, "syncGetWbiKey: get wbi key failed: ${e.message}")
            return null
        }
    }
}