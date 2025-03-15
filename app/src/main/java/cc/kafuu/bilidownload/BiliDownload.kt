package cc.kafuu.bilidownload

import android.app.Application
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.CommonLibs
import com.arialyy.aria.core.Aria
import com.chibatching.kotpref.Kotpref

class BiliDownload: Application() {
    override fun onCreate() {
        super.onCreate()
        Kotpref.init(this)
        CommonLibs.init(this)
        Aria.init(this)
        AccountManager.updateCookie()
    }
}