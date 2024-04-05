package cc.kafuu.bilidownload.common.network.repository

import android.util.Log
import cc.kafuu.bilidownload.common.core.ServerCallback
import cc.kafuu.bilidownload.common.network.service.BiliApiService
import cc.kafuu.bilidownload.common.network.model.BiliRespond
import cc.kafuu.bilidownload.common.network.model.BiliStreamDash
import cc.kafuu.bilidownload.common.network.model.BiliStreamData
import retrofit2.Callback
import retrofit2.Response

class BiliVideoRepository(private val biliApiService: BiliApiService) {
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
        ).enqueue(object : Callback<BiliRespond<BiliStreamData>> {
            override fun onResponse(
                call: retrofit2.Call<BiliRespond<BiliStreamData>>,
                respond: Response<BiliRespond<BiliStreamData>>
            ) {
                if (!respond.isSuccessful) {
                    Log.e(TAG, "Network call failed: ${respond.code()} - ${respond.message()}")
                    callback.onFailure(respond.code(), 0, respond.message())
                } else if (respond.body() == null) {
                    Log.e(TAG, "Response body is null. Error code: ${respond.code()},")
                    callback.onFailure(respond.code(), 0, respond.message())
                } else if (respond.body()?.code != 0) {
                    Log.e(
                        TAG,
                        "API call failed: ${respond.body()?.code}, message: ${respond.body()?.message}"
                    )
                    callback.onFailure(respond.code(), respond.body()?.code ?: 0, respond.message())
                } else {
                    callback.onSuccess(
                        respond.code(),
                        respond.body()?.code ?: 0,
                        respond.message(),
                        respond.body()!!.data!!.dash
                    )
                }
            }

            override fun onFailure(
                call: retrofit2.Call<BiliRespond<BiliStreamData>>,
                e: Throwable
            ) {
                e.printStackTrace()
                callback.onFailure(0, 0, e.message ?: "Unknown error")
            }
        })
    }
}