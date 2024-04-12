package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliVideoRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    companion object {
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

    fun getPlayStreamDash(
        bvid: String,
        cid: Long,
        callback: IServerCallback<BiliPlayStreamDash>
    ) {
        biliApiService.getPlayStream(null, bvid, cid, null, FNVAL_FLAGS).enqueue(callback) {
            it.dash
        }
    }

    fun getPlayStreamData(
        bvid: String,
        cid: Long,
        callback: IServerCallback<BiliPlayStreamData>
    ) {
        biliApiService.getPlayStream(null, bvid, cid, null, FNVAL_FLAGS).enqueue(callback) { it }
    }

    fun getVideoDetail(bvid: String, callback: IServerCallback<BiliVideoData>) {
        biliApiService.getVideoDetail(null, bvid).enqueue(callback) { it }
    }

    fun syncGetVideoDetail(bvid: String, onFailure: ((Int, Int, String) -> Unit)? = null): BiliVideoData? {
        return biliApiService.getVideoDetail(null, bvid).execute(onFailure) { it }
    }
}