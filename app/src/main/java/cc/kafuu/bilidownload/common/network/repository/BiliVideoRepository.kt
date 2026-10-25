package cc.kafuu.bilidownload.common.network.repository

import android.util.Log
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.NetworkConfig
import cc.kafuu.bilidownload.common.network.manager.WbiManager
import cc.kafuu.bilidownload.common.network.model.BccSubtitle
import cc.kafuu.bilidownload.common.network.model.BiliDanmakuProtoParser
import cc.kafuu.bilidownload.common.network.model.BiliHistoryDanmakuIndex
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamData
import cc.kafuu.bilidownload.common.network.model.BiliRespond
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData
import cc.kafuu.bilidownload.common.network.model.BiliSubtitleListContainer
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.model.BiliXmlDanmaku
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import cc.kafuu.bilidownload.common.network.service.BiliOriginalContentService
import cc.kafuu.bilidownload.common.utils.NetworkUtils
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** 封装视频、番剧、播放流和字幕请求，详情入口统一先生成 WBI 签名再交给网络服务。 */
class BiliVideoRepository(
    private val biliApiService: BiliApiService,
    private val biliOriginalContentService: BiliOriginalContentService
) : BiliRepository() {
    companion object {
        private const val TAG = "BiliVideoRepository"

        val FNVAL_FLAGS = listOf(
            16,     // DASH 格式	，与 MP4、FLV 格式互斥
            64,     // 是否需求 HDR 视频，需求 DASH 格式，仅 H.265 编码，需要qn=125，大会员认证
            128,    // 是否需求 4K 分辨率，该值与fourk字段协同作用，需要qn=120，大会员认证
            256,    // 是否需求杜比音频，是否需求杜比音频，需求 DASH 格式，大会员认证
            512,    // 是否需求杜比视界，需求 DASH 格式，大会员认证
            1024,   // 是否需求 8K 分辨率，需求 DASH 格式，需要qn=127，大会员认证
            2048    // 是否需求 AV1 编码，需求 DASH 格式
        ).reduce { acc, flag -> acc or flag }
    }

    /** 请求分 P 播放流，返回原始请求供页面或批量用例在取消时释放网络操作。 */
    fun requestPlayStreamDash(
        bvid: String,
        cid: Long,
        callback: IServerCallback<BiliPlayStreamDash>
    ): Call<BiliRespond<BiliPlayStreamData>> {
        return biliApiService.requestPlayStream(
            avid = null,
            bvid = bvid,
            cid = cid,
            qn = null,
            fnval = FNVAL_FLAGS
        ).also { call ->
            call.enqueue(callback = callback) { _, data -> data.dash }
        }
    }

    /** 异步请求分 P 的完整播放流数据，沿用当前统一的格式能力标记。 */
    fun requestPlayStreamData(
        bvid: String,
        cid: Long,
        callback: IServerCallback<BiliPlayStreamData>
    ) {
        biliApiService
            .requestPlayStream(avid = null, bvid = bvid, cid = cid, qn = null, fnval = FNVAL_FLAGS)
            .enqueue(callback = callback) { _, data -> data }
    }

    /** 为指定 BV 号异步生成签名，签名成功后请求视频详情，失败交回同一回调。 */
    fun requestVideoDetail(bvid: String, callback: IServerCallback<BiliVideoData>) {
        requestVideoDetailWbi(params = linkedMapOf("bvid" to bvid), callback = callback)
    }

    /** 读取列表缺失的统计；取消挂起调用时同步取消 Retrofit 请求，不弹出页面错误。 */
    suspend fun requestVideoStats(bvid: String): BiliVideoStatsRepository.Result =
        suspendCancellableCoroutine { continuation ->
            val call = biliApiService.requestVideoDetail(bvid = bvid)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback<BiliRespond<BiliVideoData>> {
                override fun onResponse(
                    call: Call<BiliRespond<BiliVideoData>>,
                    response: Response<BiliRespond<BiliVideoData>>,
                ) {
                    val body = response.body()
                    val stats = (body?.data ?: body?.result)?.stat?.toVideoStats()
                    val result = if (response.isSuccessful && body?.code == 0 && stats != null) {
                        BiliVideoStatsRepository.Result.Success(stats = stats)
                    } else {
                        BiliVideoStatsRepository.Result.Failure(
                            httpCode = response.code(),
                            apiCode = body?.code ?: 0
                        )
                    }
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onFailure(call: Call<BiliRespond<BiliVideoData>>, error: Throwable) {
                    if (continuation.isActive) {
                        continuation.resume(BiliVideoStatsRepository.Result.Failure())
                    }
                }
            })
        }

    /** 在调用线程完成签名与视频详情请求，阻塞 IO 的线程切换由调用方负责。 */
    fun syncRequestVideoDetail(
        bvid: String,
        onFailure: ((Int, Int, String) -> Unit)? = null
    ) = syncRequestVideoDetailWbi(params = linkedMapOf("bvid" to bvid), onFailure = onFailure)

    /** 按 seasonId 异步签名并请求番剧详情。 */
    fun requestSeasonDetailBySeasonId(seasonId: Long, callback: IServerCallback<BiliSeasonData>) {
        requestSeasonDetailWbi(params = linkedMapOf("season_id" to seasonId), callback = callback)
    }

    /** 按 seasonId 同步签名并请求番剧详情，调用方负责安排 IO 线程。 */
    fun syncRequestSeasonDetailBySeasonId(
        seasonId: Long,
        onFailure: ((Int, Int, String) -> Unit)? = null
    ) = syncRequestSeasonDetailWbi(
        params = linkedMapOf("season_id" to seasonId),
        onFailure = onFailure
    )

    /** 按分集 epId 异步签名并请求所属番剧详情。 */
    fun requestSeasonDetailByEpId(epId: Long, callback: IServerCallback<BiliSeasonData>) {
        requestSeasonDetailWbi(params = linkedMapOf("ep_id" to epId), callback = callback)
    }

    /** 按分集 epId 同步签名并请求番剧详情，调用方负责安排 IO 线程。 */
    fun syncRequestSeasonDetailByEpId(
        epId: Long,
        onFailure: ((Int, Int, String) -> Unit)? = null
    ) = syncRequestSeasonDetailWbi(params = linkedMapOf("ep_id" to epId), onFailure = onFailure)

    /** 签名成功后使用完整地址发起异步请求，签名失败时直接交回原回调。 */
    private fun requestVideoDetailWbi(
        params: Map<String, Any>,
        callback: IServerCallback<BiliVideoData>
    ) {
        // 签名回调提供查询串；签名失败时不能继续发出未签名的详情请求。
        WbiManager.asyncGenerateSignature(
            paramMap = params,
            callback = object : IServerCallback<String> {
                override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                    biliApiService
                        .requestVideoDetail(
                            fullUrl = NetworkConfig.buildFullUrl(
                                path = "/x/web-interface/view",
                                params = data
                            )
                        )
                        .enqueue(callback = callback) { _, video -> video }
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    callback.onFailure(httpCode = httpCode, code = code, message = message)
                }
            })
    }

    /** 同步生成签名后读取视频详情；签名异常优先交给失败回调，没有回调时继续抛出。 */
    private fun syncRequestVideoDetailWbi(
        params: Map<String, Any>,
        onFailure: ((Int, Int, String) -> Unit)?
    ): BiliVideoData? {
        // 保留签名阶段的失败出口，避免失败后仍访问业务接口。
        val signedParams = try {
            WbiManager.syncGenerateSignature(paramMap = params)
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure?.invoke(0, 0, e.message ?: "WBI signing failed") ?: throw e
            return null
        }
        return biliApiService
            .requestVideoDetail(
                fullUrl = NetworkConfig.buildFullUrl(
                    path = "/x/web-interface/view",
                    params = signedParams
                )
            )
            .execute(onFailure = onFailure) { _, data -> data }
    }

    /** 番剧参数签名后再发起请求，回调只接收所属阶段的成功或失败结果。 */
    private fun requestSeasonDetailWbi(
        params: Map<String, Any>,
        callback: IServerCallback<BiliSeasonData>
    ) {
        // 番剧签名与业务响应分别处理，内部签名数据不作为番剧结果交给上层。
        WbiManager.asyncGenerateSignature(
            paramMap = params,
            callback = object : IServerCallback<String> {
                override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                    biliApiService
                        .requestSeasonDetail(
                            fullUrl = NetworkConfig.buildFullUrl(
                                path = "/pgc/view/web/season",
                                params = data
                            )
                        )
                        .enqueue(callback = callback) { _, season -> season }
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    callback.onFailure(httpCode = httpCode, code = code, message = message)
                }
            })
    }

    /** 同步签名并读取番剧详情，签名失败沿用调用方提供的错误处理方式。 */
    private fun syncRequestSeasonDetailWbi(
        params: Map<String, Any>,
        onFailure: ((Int, Int, String) -> Unit)?
    ): BiliSeasonData? {
        // 签名失败时保留已有的回调或抛出语义，不构造未经签名的回退请求。
        val signedParams = try {
            WbiManager.syncGenerateSignature(paramMap = params)
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure?.invoke(0, 0, e.message ?: "WBI signing failed") ?: throw e
            return null
        }
        return biliApiService
            .requestSeasonDetail(
                fullUrl = NetworkConfig.buildFullUrl(
                    path = "/pgc/view/web/season",
                    params = signedParams
                )
            )
            .execute(onFailure = onFailure) { _, data -> data }
    }

    /**
     * 请求视频弹幕数据
     * @param cid 视频的cid
     * @param callback 回调函数，返回解析后的弹幕列表
     */
    fun requestDanmakuXmlData(
        cid: Long,
        callback: IServerCallback<List<BiliXmlDanmaku>>
    ) {
        biliOriginalContentService.requestDanmakuXml(cid = cid)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    val list = try {
                        val body =
                            response.body()?.let { NetworkUtils.decompressDeflate(body = it) }
                                ?: run {
                                    callback.onFailure(
                                        httpCode = response.code(),
                                        code = 0,
                                        message = CommonLibs.getString(id = R.string.error_unable_to_retrieve_content)
                                    )
                                    return
                                }
                        BiliXmlDanmaku.parseFromXml(xmlString = body)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(
                            httpCode = 0,
                            code = 0,
                            message = e.message ?: CommonLibs.getString(id = R.string.error_unknown)
                        )
                        null
                    }
                    Log.d(TAG, "requestDanmaku: $list")
                    list?.run {
                        callback.onSuccess(
                            httpCode = response.code(),
                            code = 0,
                            message = "",
                            data = this
                        )
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, error: Throwable) {
                    error.printStackTrace()
                    callback.onFailure(
                        httpCode = 0,
                        code = 0,
                        message = error.message ?: CommonLibs.getString(id = R.string.error_unknown)
                    )
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
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    try {
                        val body = response.body()?.string() ?: run {
                            callback.onFailure(
                                httpCode = response.code(),
                                code = 0,
                                message = CommonLibs.getString(id = R.string.error_unable_to_retrieve_content)
                            )
                            return
                        }

                        val json = JsonParser.parseString(body).asJsonObject
                        val indexData = BiliHistoryDanmakuIndex.fromJson(json = json)

                        if (indexData.isSuccess()) {
                            callback.onSuccess(
                                httpCode = response.code(),
                                code = 0,
                                message = "",
                                data = indexData
                            )
                        } else {
                            callback.onFailure(
                                httpCode = response.code(),
                                code = indexData.code,
                                message = indexData.message
                            )
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(
                            httpCode = 0,
                            code = 0,
                            message = e.message ?: CommonLibs.getString(id = R.string.error_unknown)
                        )
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, error: Throwable) {
                    error.printStackTrace()
                    callback.onFailure(
                        httpCode = 0,
                        code = 0,
                        message = error.message ?: CommonLibs.getString(id = R.string.error_unknown)
                    )
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
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    try {
                        val body = response.body()?.bytes() ?: run {
                            callback.onFailure(
                                httpCode = response.code(),
                                code = 0,
                                message = CommonLibs.getString(id = R.string.error_unable_to_retrieve_content)
                            )
                            return
                        }

                        // 使用protobuf解析器解析弹幕数据
                        val danmakuList = BiliDanmakuProtoParser.parseFromProto(data = body)

                        Log.d(
                            TAG,
                            "requestSegmentDanmaku: segment=$segmentIndex, count=${danmakuList.size}"
                        )

                        callback.onSuccess(
                            httpCode = response.code(),
                            code = 0,
                            message = "",
                            data = danmakuList
                        )

                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(
                            httpCode = 0,
                            code = 0,
                            message = e.message ?: CommonLibs.getString(id = R.string.error_unknown)
                        )
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, error: Throwable) {
                    error.printStackTrace()
                    callback.onFailure(
                        httpCode = 0,
                        code = 0,
                        message = error.message ?: CommonLibs.getString(id = R.string.error_unknown)
                    )
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
        requestSegmentDanmaku(
            cid = cid,
            segmentIndex = 1,
            callback = object : IServerCallback<List<BiliXmlDanmaku>> {
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
                            callback.onSuccess(
                                httpCode = httpCode,
                                code = 0,
                                message = "",
                                data = allDanmaku
                            )
                            return
                        }

                        requestSegmentDanmaku(
                            cid = cid,
                            segmentIndex = currentSegment,
                            callback = object : IServerCallback<List<BiliXmlDanmaku>> {
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
                                        callback.onSuccess(
                                            httpCode = httpCode,
                                            code = 0,
                                            message = "",
                                            data = allDanmaku
                                        )
                                    }
                                }

                                override fun onFailure(httpCode: Int, code: Int, message: String) {
                                    // 获取失败，但已经有部分数据了，返回已有数据
                                    Log.w(
                                        TAG,
                                        "Failed to fetch segment $currentSegment, returning ${allDanmaku.size} danmaku"
                                    )
                                    callback.onSuccess(
                                        httpCode = httpCode,
                                        code = 0,
                                        message = "",
                                        data = allDanmaku
                                    )
                                }
                            })
                    }

                    fetchNextSegment()
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    callback.onFailure(httpCode = httpCode, code = code, message = message)
                }
            })
    }

    /**
     * 请求字幕列表
     */
    fun requestSubtitleList(
        cid: Long,
        bvid: String,
        callback: IServerCallback<BiliSubtitleListContainer>
    ) {
        val params = linkedMapOf<String, Any>(
            "bvid" to bvid,
            "cid" to cid
        )
        WbiManager.asyncGenerateSignature(
            paramMap = params,
            callback = object : IServerCallback<String> {
                override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                    val fullUrl =
                        NetworkConfig.buildFullUrl(path = "/x/player/wbi/v2", params = data)
                    biliOriginalContentService.requestSubtitleList(url = fullUrl)
                        .enqueue(object : Callback<ResponseBody> {
                            override fun onResponse(
                                call: Call<ResponseBody?>,
                                response: Response<ResponseBody?>
                            ) {
                                try {
                                    val bodyString = response.body()?.string() ?: run {
                                        callback.onFailure(
                                            httpCode = response.code(),
                                            code = 0,
                                            message = CommonLibs.getString(id = R.string.error_unable_to_retrieve_content)
                                        )
                                        return
                                    }
                                    val container = Gson().fromJson(
                                        bodyString,
                                        BiliSubtitleListContainer::class.java
                                    )
                                    if (container.isSuccess()) {
                                        callback.onSuccess(
                                            httpCode = response.code(),
                                            code = container.code,
                                            message = container.message,
                                            data = container
                                        )
                                    } else {
                                        callback.onFailure(
                                            httpCode = response.code(),
                                            code = container.code,
                                            message = container.message
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    callback.onFailure(
                                        httpCode = 0,
                                        code = 0,
                                        message = e.message
                                            ?: CommonLibs.getString(id = R.string.error_unknown)
                                    )
                                }
                            }

                            override fun onFailure(call: Call<ResponseBody?>, error: Throwable) {
                                error.printStackTrace()
                                callback.onFailure(
                                    httpCode = 0,
                                    code = 0,
                                    message = error.message
                                        ?: CommonLibs.getString(id = R.string.error_unknown)
                                )
                            }
                        })
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    callback.onFailure(httpCode = httpCode, code = code, message = message)
                }
            })
    }

    /**
     * 请求具体字幕文件数据，并解析为BccSubtitle
     */
    fun requestSubtitleData(
        url: String,
        callback: IServerCallback<BccSubtitle>
    ) {
        // url可能是 //aisubtitle.hdslb.com/...
        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        biliOriginalContentService.requestSubtitleData(url = fullUrl)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    try {
                        val bodyString = response.body()?.string() ?: run {
                            callback.onFailure(
                                httpCode = response.code(),
                                code = 0,
                                message = CommonLibs.getString(id = R.string.error_unable_to_retrieve_content)
                            )
                            return
                        }
                        val bccSubtitle = Gson().fromJson(bodyString, BccSubtitle::class.java)
                        callback.onSuccess(
                            httpCode = response.code(),
                            code = 0,
                            message = "",
                            data = bccSubtitle
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback.onFailure(
                            httpCode = 0,
                            code = 0,
                            message = e.message ?: CommonLibs.getString(id = R.string.error_unknown)
                        )
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, error: Throwable) {
                    error.printStackTrace()
                    callback.onFailure(
                        httpCode = 0,
                        code = 0,
                        message = error.message ?: CommonLibs.getString(id = R.string.error_unknown)
                    )
                }
            })
    }
}
