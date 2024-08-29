package cc.kafuu.bilidownload.common.network.service

import cc.kafuu.bilidownload.common.network.model.BiliAccountData
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteDetailsData
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteListData
import cc.kafuu.bilidownload.common.network.model.BiliHistoryData
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamData
import cc.kafuu.bilidownload.common.network.model.BiliRespond
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.network.model.BiliSearchMediaResultData
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.model.BiliWbiData
import cc.kafuu.bilidownload.common.network.model.MyBiliAccountData
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url


interface BiliApiService {

    @GET("x/web-interface/nav")
    fun requestWbiInterfaceNav(): Call<BiliRespond<BiliWbiData>>

    @GET("x/player/playurl")
    fun requestPlayStream(
        @Query("avid") avid: Long?,
        @Query("bvid") bvid: String?,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int? = null,
        @Query("fnval") fnval: Int? = 1,
        @Query("fnver") fnver: Int? = 0,
        @Query("fourk") fourk: Int? = 1,
        @Query("platform") platform: String? = null,
        @Query("high_quality") highQuality: Int? = null
    ): Call<BiliRespond<BiliPlayStreamData>>

    @GET("x/web-interface/view")
    fun requestVideoDetail(
        @Query("aid") aid: Int? = null,
        @Query("bvid") bvid: String? = null
    ): Call<BiliRespond<BiliVideoData>>

    @GET("pgc/view/web/season")
    fun requestSeasonDetail(
        @Query("season_id") seasonId: Long? = null,
        @Query("ep_id") epId: Long? = null
    ): Call<BiliRespond<BiliSeasonData>>

    @GET("x/member/web/account")
    fun requestMyAccount(): Call<BiliRespond<MyBiliAccountData>>

    @GET("x/v3/fav/folder/created/list-all")
    fun requestUserFavorites(
        @Query("up_mid") mid: Long,
        @Query("type") type: Int,
    ): Call<BiliRespond<BiliFavoriteListData>>

    @GET("x/v3/fav/resource/list")
    fun requestFavoriteDetails(
        @Query("media_id") id: Long,
        @Query("ps") ps: Int,
        @Query("pn") pn: Int,
    ): Call<BiliRespond<BiliFavoriteDetailsData>>

    @GET("x/web-interface/history/cursor")
    fun requestHistoryCursor(
        @Query("max") max: Long,
        @Query("business") business: String = "",
        @Query("view_at") viewAt: Long = 0,
        @Query("type") type: String = "all",
        @Query("ps") ps: Int
    ): Call<BiliRespond<BiliHistoryData>>

    @GET
    fun requestAccountData(@Url fullUrl: String?): Call<BiliRespond<BiliAccountData>>

    @GET
    fun requestSearchVideo(@Url fullUrl: String?): Call<BiliRespond<BiliSearchData<BiliSearchVideoResultData>>>

    @GET
    fun requestSearchMedia(@Url fullUrl: String?): Call<BiliRespond<BiliSearchData<BiliSearchMediaResultData>>>
}
