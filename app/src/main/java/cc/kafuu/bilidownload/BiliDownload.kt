package cc.kafuu.bilidownload

import android.app.Application
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.CommonLibs
import com.chibatching.kotpref.Kotpref

class BiliDownload: Application() {
    override fun onCreate() {
        super.onCreate()
        Kotpref.init(this)
        CommonLibs.init(this)
        AccountManager.updateCookie()
    }
}
