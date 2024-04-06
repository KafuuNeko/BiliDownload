package cc.kafuu.bilidownload.view.activity

import android.util.Log
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.core.IServerCallback
import cc.kafuu.bilidownload.common.jniexport.FFMpegJNI
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
import cc.kafuu.bilidownload.databinding.ActivityMainBinding
import cc.kafuu.bilidownload.viewmodel.MainViewModel

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

        NetworkManager.biliVideoRepository.getPlayStreamDash("BV1e34y1V7EF", 1290262561L, 32, object :
            IServerCallback<BiliPlayStreamDash> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliPlayStreamDash
            ) {
                Log.d(TAG, "onSuccess: httpCode: $httpCode, code: $code, message: $message, data: $data")
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                Log.e(TAG, "onFailure: httpCode: $httpCode, code: $code, message: $message")
            }

        })
    }
}