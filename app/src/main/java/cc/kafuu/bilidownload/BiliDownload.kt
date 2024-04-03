package cc.kafuu.bilidownload

import android.app.Application
import cc.kafuu.bilidownload.common.utils.CommonLibs

class BiliDownload: Application() {
    override fun onCreate() {
        super.onCreate()
        CommonLibs.init(this)
    }
}