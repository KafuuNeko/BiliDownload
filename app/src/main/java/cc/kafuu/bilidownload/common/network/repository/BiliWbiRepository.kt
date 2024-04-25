package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import java.io.IOException

class BiliWbiRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    companion object {
        private const val TAG = "BiliWbiRepository"

        private fun String.extractBetweenWbiAndDot(): String {
            return this.substringAfter("wbi/").substringBefore(".")
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun syncGetWbiKey(onFailure: ((Int, Int, String) -> Unit)? = null): Pair<String, String>? {
        //Wbi接口相对特殊，就算respond code非0也存在wbi，因此无需检查respond code
        return biliApiService.getWbiInterfaceNav().execute(onFailure, false) {
            Pair(
                it.wbiImg.imgUrl.extractBetweenWbiAndDot(),
                it.wbiImg.subUrl.extractBetweenWbiAndDot()
            )
        }
    }

    fun getWbiKey(callback: IServerCallback<Pair<String, String>>) {
        //Wbi接口相对特殊，就算respond code非0也存在wbi，因此无需检查respond code
        biliApiService.getWbiInterfaceNav().enqueue(callback, false) {
            Pair(
                it.wbiImg.imgUrl.extractBetweenWbiAndDot(),
                it.wbiImg.subUrl.extractBetweenWbiAndDot()
            )
        }
    }
}