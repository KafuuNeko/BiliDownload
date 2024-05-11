package cc.kafuu.bilidownload

import android.app.Application
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.utils.CommonLibs
import com.arialyy.aria.core.Aria

class BiliDownload: Application() {
    override fun onCreate() {
        super.onCreate()
        CommonLibs.init(this)
        Aria.init(this)
        AccountManager.updateCookie()
    }
}