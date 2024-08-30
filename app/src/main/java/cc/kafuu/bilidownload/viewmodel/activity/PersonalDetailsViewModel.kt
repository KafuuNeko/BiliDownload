package cc.kafuu.bilidownload.viewmodel.activity

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction
import cc.kafuu.bilidownload.common.model.bili.BiliAccountModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliAccountData

class PersonalDetailsViewModel : CoreViewModel() {
    // 用户mid
    private var mMid: Long = 0

    // 账号基本信息
    private val mBiliAccountLiveData = MutableLiveData<BiliAccountModel>()
    val biliAccountLiveData = mBiliAccountLiveData.liveData()

    fun initData(mid: Long) {
        mMid = mid
        loadAccountData()
    }

    /**
     * 加载账户信息
     * 如果要加载的账户是当前登录的账户则直接取本地数据，否则将请求账户信息
     */
    private fun loadAccountData() {
        if (mMid == AccountManager.accountLiveData.value?.mid) {
            mBiliAccountLiveData.value = AccountManager.accountLiveData.value
            return
        }
        object : IServerCallback<BiliAccountData> {
            override fun onSuccess(
                httpCode: Int,
                code: Int,
                message: String,
                data: BiliAccountData
            ) {
                mBiliAccountLiveData.postValue(BiliAccountModel.create(data))
            }

            override fun onFailure(httpCode: Int, code: Int, message: String) {
                popMessage(ToastMessageAction(message))
            }
        }.also {
            NetworkManager.biliAccountRepository.requestAccountData(mMid, it)
        }
    }

}