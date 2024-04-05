package cc.kafuu.bilidownload.common.network.repository

import cc.kafuu.bilidownload.common.core.ServerCallback
import cc.kafuu.bilidownload.common.network.model.BiliStreamDash
import cc.kafuu.bilidownload.common.network.service.BiliApiService

class BiliVideoRepository(private val biliApiService: BiliApiService) : BiliRepository() {
    companion object {
        private const val TAG = "VideoStreamRepository"
    }

    private fun getVideoStreamDash(
        bvid: String,
        cid: Long,
        qn: Int,
        callback: ServerCallback<BiliStreamDash>
    ) {
        biliApiService.getVideoStreamUrl(
            null,
            bvid,
            cid,
            qn,
            16 or 64 or 128 or 256 or 512 or 1024 or 2048
        ).enqueue(callback) {
            it.dash
        }
    }
}