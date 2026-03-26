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

    /**
     * 获取历史弹幕索引（需要登录）
     * 返回指定月份中有弹幕的日期列表
     * API文档: https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/danmaku/history.md
     */
    @GET("https://api.bilibili.com/x/v2/dm/history/index")
    fun requestHistoryIndex(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("month") month: String
    ): Call<ResponseBody>

    /**
     * 获取分段弹幕（需要登录）
     * 将弹幕按时间段分段获取，可以获取比实时弹幕更多的数据
     * segment_index: 分段索引，从1开始
     */
    @GET("https://api.bilibili.com/x/v2/dm/web/seg.so")
    fun requestSegmentDanmaku(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("segment_index") segmentIndex: Int
    ): Call<ResponseBody>

    /**
     * 获取视频字幕列表
     */
    @GET
    fun requestSubtitleList(
        @retrofit2.http.Url url: String
    ): Call<ResponseBody>

    /**
     * 下载具体字幕文件 (BCC JSON格式)
     */
    @GET
    fun requestSubtitleData(
        @retrofit2.http.Url url: String
    ): Call<ResponseBody>
}
