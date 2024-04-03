package cc.kafuu.bilidownload.view.activity

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.jniexport.FFMpegJNI
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
    }
}