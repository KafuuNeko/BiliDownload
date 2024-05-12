package cc.kafuu.bilidownload.common.network.service

import cc.kafuu.bilidownload.common.network.model.BiliRespond
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST


interface BiliPassportService {
    @FormUrlEncoded
    @POST("login/exit/v2")
    fun requestLogout(@Field("biliCSRF") biliCSRF: String): Call<BiliRespond<JsonObject>>
}
