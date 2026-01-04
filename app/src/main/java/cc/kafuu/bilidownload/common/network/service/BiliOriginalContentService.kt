package cc.kafuu.bilidownload.common.network.service

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query


interface BiliOriginalContentService {
    @GET("https://space.bilibili.com/{mid}/dynamic")
    fun getUserDynamic(@Path("mid") mid: Long): Call<String>

    // "https://comment.bilibili.com/$cid.xml"
    @GET("https://api.bilibili.com/x/v1/dm/list.so")
    fun requestDanmakuXml(@Query("oid") cid: Long): Call<ResponseBody>

}
