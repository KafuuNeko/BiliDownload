package cc.kafuu.bilidownload.common.network.service

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path


interface BiliOriginalContentService {
    @GET("https://space.bilibili.com/{mid}/dynamic")
    fun getUserDynamic(@Path("mid") mid: Long): Call<String>
}
