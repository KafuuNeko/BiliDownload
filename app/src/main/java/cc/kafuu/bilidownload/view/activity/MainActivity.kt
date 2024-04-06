package cc.kafuu.bilidownload.view.activity

import android.util.Log
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.core.IServerCallback
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.jniexport.FFMpegJNI
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamData
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.databinding.ActivityMainBinding
import cc.kafuu.bilidownload.service.DownloadService
import cc.kafuu.bilidownload.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking

class MainActivity : CoreActivity<ActivityMainBinding, MainViewModel>(
    MainViewModel::class.java,
    R.layout.activity_main,
    BR.viewModel
) {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun init() {
        mViewDataBinding.sampleText.text = FFMpegJNI.ffmpegInfo()


        NetworkManager.biliVideoRepository.getPlayStreamData(
            "BV1e34y1V7EF",
            1290262561L,
            null,
            object :
                IServerCallback<BiliPlayStreamData> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: BiliPlayStreamData
                ) {
                    Log.d(
                        TAG,
                        "onSuccess: httpCode: $httpCode, code: $code, message: $message, data: $data"
                    )

                    runBlocking {
                        val taskId = CommonLibs.requireAppDatabase().downloadTaskDao().insert(
                            DownloadTaskEntity(
                                0, null, false, "BV1e34y1V7EF", 1290262561L,
                                data.acceptQuality[0], data.dash.video[0].id, data.dash.audio[0].id
                            )
                        )
                        DownloadService.startDownload(this@MainActivity, taskId)
                        Log.d(TAG, "onSuccess: $taskId")
                    }

                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    Log.e(TAG, "onFailure: httpCode: $httpCode, code: $code, message: $message")
                }

            })
    }
}