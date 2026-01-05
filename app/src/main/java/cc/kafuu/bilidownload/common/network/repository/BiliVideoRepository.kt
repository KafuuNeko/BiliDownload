package cc.kafuu.bilidownload.common.network.repository

import android.util.Log
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.model.BiliDanmakuProtoParser
import cc.kafuu.bilidownload.common.network.model.BiliHistoryDanmakuIndex
import cc.kafuu.bilidownload.common.network.model.BiliXmlDanmaku
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamData
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import cc.kafuu.bilidownload.common.network.service.BiliOriginalContentService
import cc.kafuu.bilidownload.common.utils.NetworkUtils
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import com.google.gson.JsonParser

class BiliVideoRepository(
    private val biliApiService: BiliApiService,
    private val biliOriginalContentService: BiliOriginalContentService
) : BiliRepository() {
    companion object {
        private const val TAG = "BiliVideoRepository"

        val FNVAL_FLAGS = listOf(
//            1,    // MP4 格式，仅 H.264 编码（与 FLV、DASH 格式互斥）
            16,     // DASH 格式	，与 MP4、FLV 格式互斥
            64,     // 是否需求 HDR 视频，需求 DASH 格式，仅 H.265 编码，需要qn=125，大会员认证
            128,    // 是否需求 4K 分辨率，该值与fourk字段协同作用，需要qn=120，大会员认证
            256,    // 是否需求杜比音频，是否需求杜比音频，需求 DASH 格式，大会员认证
            512,    // 是否需求杜比视界，需求 DASH 格式，大会员认证
            1024,   // 是否需求 8K 分辨率，需求 DASH 格式，需要qn=127，大会员认证
            2048    // 是否需求 AV1 编码，需求 DASH 格式
        ).reduce { acc, flag -> acc or flag }
    }

    fun requestPlayStreamDash(
        bvid: String,
        cid: Long,
        callback: IServerCallback<BiliPlayStreamDash>
    ) {
        biliApiService
            .requestPlayStream(null, bvid, cid, null, FNVAL_FLAGS)
            .enqueue(callback) { _, data -> data.dash }
    }

    fun requestPlayStreamData(
        bvid: String,
        cid: Long,
        callback: IServerCallback<BiliPlayStreamData>
    ) {
        biliApiService
            .requestPlayStream(null, bvid, cid, null, FNVAL_FLAGS)
            .enqueue(callback) { _, data -> data }
    }

    fun requestVideoDetail(bvid: String, callback: IServerCallback<BiliVideoData>) {
        biliApiService
            .requestVideoDetail(null, bvid)
            .enqueue(callback) { _, data -> data }
    }

    fun syncRequestVideoDetail(
        bvid: String,
        onFailure: ((Int, Int, String) -> Unit)? = null
    ) = biliApiService
        .requestVideoDetail(null, bvid)
        .execute(onFailure) { _, data -> data }

    fun requestSeasonDetailBySeasonId(seasonId: Long, callback: IServerCallback<BiliSeasonData>) {
        biliApiService
            .requestSeasonDetail(seasonId, null)
            .enqueue(callback) { _, data -> data }
    }

    fun syncRequestSeasonDetailBySeasonId(
        seasonId: Long,
        onFailure: ((Int, Int, String) -> Unit)? = null
    ) = biliApiService
        .requestSeasonDetail(seasonId, null)
        .execute(onFailure) { _, data -> data }

    fun requestSeasonDetailByEpId(epId: Long, callback: IServerCallback<BiliSeasonData>) {
        biliApiService
            .requestSeasonDetail(null, epId)
            .enqueue(callback) { _, data -> data }
    }

    fun syncRequestSeasonDetailByEpId(
        epId: Long,
        onFailure: ((Int, Int, String) -> Unit)? = null
    ) = biliApiService
        .requestSeasonDetail(null, epId)
        .execute(onFailure) { _, data -> data }

    /**
     * 请求视频弹幕数据
     * @param cid 视频的cid
     * @param callback 回调函数，返回解析后的弹幕列表
     */
    fun requestDanmakuXmlData(
        cid: Long,
        callback: IServerCallback<List<BiliXmlDanmaku>>
    ) {
        biliOriginalContentService.requestDanmakuXml(cid)
            .enqueue(object : retrofit2.Callback<ResponseBody> {
                override fun onResponse(
                    p0: Call<ResponseBody?>,
                    p1: Response<ResponseBody?>
                ) {
                    val list = try {
                        val body = p1.body()?.let { NetworkUtils.decompressDeflate(it) } ?: run {
                            callback.onFailure(p1.code(), 0, CommonLibs.getString(R.string.error_unable_to_retrieve_content))
                            return
                        }
                        BiliXmlDanmaku.parseFromXml(body)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(0, 0, e.message ?: CommonLibs.getString(R.string.error_unknown))
                        null
                    }
                    Log.d(TAG, "requestDanmaku: $list")
                    list?.run {
                        callback.onSuccess(p1.code(), 0, "", this)
                    }
                }

                override fun onFailure(p0: Call<ResponseBody?>, p1: Throwable) {
                    p1.printStackTrace()
                    callback.onFailure(0, 0, p1.message ?: CommonLibs.getString(R.string.error_unknown))
                }
            })
    }

    /**
     * 请求历史弹幕索引（需要登录）
     * @param cid 视频的cid
     * @param month 月份，格式：2024-01
     * @param callback 回调函数，返回日期列表
     */
    fun requestHistoryDanmakuIndex(
        cid: Long,
        month: String,
        callback: IServerCallback<BiliHistoryDanmakuIndex>
    ) {
        biliOriginalContentService.requestHistoryIndex(oid = cid, month = month)
            .enqueue(object : retrofit2.Callback<ResponseBody> {
                override fun onResponse(
                    p0: Call<ResponseBody?>,
                    p1: Response<ResponseBody?>
                ) {
                    try {
                        val body = p1.body()?.string() ?: run {
                            callback.onFailure(
                                p1.code(),
                                0,
                                CommonLibs.getString(R.string.error_unable_to_retrieve_content)
                            )
                            return
                        }

                        val json = JsonParser.parseString(body).asJsonObject
                        val indexData = BiliHistoryDanmakuIndex.fromJson(json)

                        if (indexData.isSuccess()) {
                            callback.onSuccess(p1.code(), 0, "", indexData)
                        } else {
                            callback.onFailure(p1.code(), indexData.code, indexData.message)
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(0, 0, e.message ?: CommonLibs.getString(R.string.error_unknown))
                    }
                }

                override fun onFailure(p0: Call<ResponseBody?>, p1: Throwable) {
                    p1.printStackTrace()
                    callback.onFailure(0, 0, p1.message ?: CommonLibs.getString(R.string.error_unknown))
                }
            })
    }

    /**
     * 请求分段弹幕（需要登录）
     * @param cid 视频的cid
     * @param segmentIndex 分段索引，从1开始
     * @param callback 回调函数，返回解析后的弹幕列表
     */
    fun requestSegmentDanmaku(
        cid: Long,
        segmentIndex: Int,
        callback: IServerCallback<List<BiliXmlDanmaku>>
    ) {
        biliOriginalContentService.requestSegmentDanmaku(oid = cid, segmentIndex = segmentIndex)
            .enqueue(object : retrofit2.Callback<ResponseBody> {
                override fun onResponse(
                    p0: Call<ResponseBody?>,
                    p1: Response<ResponseBody?>
                ) {
                    try {
                        val body = p1.body()?.bytes() ?: run {
                            callback.onFailure(
                                p1.code(),
                                0,
                                CommonLibs.getString(R.string.error_unable_to_retrieve_content)
                            )
                            return
                        }

                        // 使用protobuf解析器解析弹幕数据
                        val danmakuList = BiliDanmakuProtoParser.parseFromProto(body)

                        Log.d(TAG, "requestSegmentDanmaku: segment=$segmentIndex, count=${danmakuList.size}")

                        callback.onSuccess(p1.code(), 0, "", danmakuList)

                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(0, 0, e.message ?: CommonLibs.getString(R.string.error_unknown))
                    }
                }

                override fun onFailure(p0: Call<ResponseBody?>, p1: Throwable) {
                    p1.printStackTrace()
                    callback.onFailure(0, 0, p1.message ?: CommonLibs.getString(R.string.error_unknown))
                }
            })
    }

    /**
     * 请求全量弹幕（需要登录）
     * 自动分段获取所有弹幕并合并
     * @param cid 视频的cid
     * @param callback 回调函数，返回解析后的弹幕列表
     */
    fun requestFullDanmaku(
        cid: Long,
        callback: IServerCallback<List<BiliXmlDanmaku>>
    ) {
        // 首先尝试获取第一个分段
        requestSegmentDanmaku(cid, 1, object : IServerCallback<List<BiliXmlDanmaku>> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: List<BiliXmlDanmaku>
            ) {
                // 第一个分段有数据，继续获取后续分段
                val allDanmaku = mutableListOf<BiliXmlDanmaku>()
                allDanmaku.addAll(data)

                // 继续获取更多分段（最多尝试10个分段）
                var currentSegment = 2
                val maxSegments = 10

                fun fetchNextSegment() {
                    if (currentSegment > maxSegments) {
                        callback.onSuccess(httpCode, 0, "", allDanmaku)
                        return
                    }

                    requestSegmentDanmaku(cid, currentSegment, object : IServerCallback<List<BiliXmlDanmaku>> {
                        override fun onSuccess(
                            httpCode: Int,
                            code: Int,
                            message: String,
                            data: List<BiliXmlDanmaku>
                        ) {
                            if (data.isNotEmpty()) {
                                allDanmaku.addAll(data)
                                currentSegment++
                                fetchNextSegment()
                            } else {
                                // 没有更多数据了
                                callback.onSuccess(httpCode, 0, "", allDanmaku)
                            }
                        }

                        override fun onFailure(httpCode: Int, code: Int, message: String) {
                            // 获取失败，但已经有部分数据了，返回已有数据
                            Log.w(TAG, "Failed to fetch segment $currentSegment, returning ${allDanmaku.size} danmaku")
                            callback.onSuccess(httpCode, 0, "", allDanmaku)
                        }
                    })
                }

                fetchNextSegment()
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                callback.onFailure(httpCode, code, message)
            }
        })
    }
}