package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.core.ServerCallback
import cc.kafuu.bilidownload.common.network.model.BiliStreamDash
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliVideoRepository(biliApiService: BiliApiService) : BiliRepository(biliApiService) {
    companion object {
        private const val TAG = "VideoStreamRepository"

        object FnvalFlags {
            const val FORMAT_MP4 = 1
            const val FORMAT_DASH = 16
            const val REQUIRE_HDR = 64
            const val REQUIRE_4K = 128
            const val REQUIRE_DOLBY_AUDIO = 256
            const val REQUIRE_DOLBY_VISION = 512
            const val REQUIRE_8K = 1024
            const val REQUIRE_AV1 = 2048
        }

        const val DEFAULT_FLAGS = FnvalFlags.FORMAT_DASH or FnvalFlags.REQUIRE_HDR or
                FnvalFlags.REQUIRE_4K or FnvalFlags.REQUIRE_DOLBY_AUDIO or
                FnvalFlags.REQUIRE_DOLBY_VISION or FnvalFlags.REQUIRE_8K or FnvalFlags.REQUIRE_AV1
    }

    private fun getPlayStreamDash(
        bvid: String,
        cid: Long,
        qn: Int,
        fnval: Int = DEFAULT_FLAGS,
        callback: ServerCallback<BiliStreamDash>
    ) {
        biliApiService.getPlayStream(null, bvid, cid, qn, fnval).enqueue(callback) {
            it.dash
        }
    }

    private fun getPlayStreamDash(
        avid: Long,
        cid: Long,
        qn: Int,
        fnval: Int = DEFAULT_FLAGS,
        callback: ServerCallback<BiliStreamDash>
    ) {
        biliApiService.getPlayStream(avid, null, cid, qn, fnval).enqueue(callback) {
            it.dash
        }
    }
}