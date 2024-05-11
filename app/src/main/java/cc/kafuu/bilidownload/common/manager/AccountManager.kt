package cc.kafuu.bilidownload.common.manager

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.model.bili.BiliAccount

object AccountManager {
    val cookies = MutableLiveData<String>(null)
    val account = MutableLiveData<BiliAccount>(null)
}