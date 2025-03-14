package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import cc.kafuu.bilidownload.common.network.service.BiliOriginalContentService
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.jsoup.Jsoup
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class BiliRiskControlRepository(
    private val biliApiService: BiliApiService,
    private val biliOriginalContentService: BiliOriginalContentService
) : BiliRepository() {
    companion object {
        private const val TAG = "BiliWbiRepository"

        private fun String.extractBetweenWbiAndDot(): String {
            return this.substringAfter("wbi/").substringBefore(".")
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun syncRequestWbiKey(onFailure: ((Int, Int, String) -> Unit)? = null): Pair<String, String>? {
        //Wbi接口相对特殊，就算respond code非0也存在wbi，因此无需检查respond code
        return biliApiService.requestWbiInterfaceNav().execute(onFailure, false) {
            Pair(
                it.wbiImg.imgUrl.extractBetweenWbiAndDot(),
                it.wbiImg.subUrl.extractBetweenWbiAndDot()
            )
        }
    }

    fun requestWbiKey(callback: IServerCallback<Pair<String, String>>) {
        //Wbi接口相对特殊，就算respond code非0也存在wbi，因此无需检查respond code
        biliApiService.requestWbiInterfaceNav().enqueue(callback, false) {
            Pair(
                it.wbiImg.imgUrl.extractBetweenWbiAndDot(),
                it.wbiImg.subUrl.extractBetweenWbiAndDot()
            )
        }
    }

    fun requestUserAccessId(mid: Long, callback: IServerCallback<String>) {
        biliOriginalContentService.getUserDynamic(mid).enqueue(object : retrofit2.Callback<String> {
            override fun onResponse(
                p0: Call<String?>,
                p1: Response<String?>
            ) {
                val body = p1.body() ?: run {
                    callback.onFailure(p1.code(), 0, "Unable to retrieve web content")
                    return
                }
                val doc = Jsoup.parse(body)
                val renderData = doc.selectFirst("script#\\__RENDER_DATA__")?.data() ?: run {
                    callback.onFailure(p1.code(), 0, "Unable to retrieve render data")
                    return
                }
                val accessId = runCatching {
                    val json = URLDecoder.decode(renderData, StandardCharsets.UTF_8.toString())
                    Gson().fromJson(json, JsonObject::class.java).get("access_id").asString
                }.getOrNull()
                if (accessId == null) {
                    callback.onFailure(p1.code(), 0, "Unable to retrieve access id")
                    return
                }
                callback.onSuccess(p1.code(), 0, "", accessId)
            }

            override fun onFailure(p0: Call<String?>, p1: Throwable) {
                p1.printStackTrace()
                callback.onFailure(0, 0, p1.message ?: "Unknown error")
            }
        })
    }
}