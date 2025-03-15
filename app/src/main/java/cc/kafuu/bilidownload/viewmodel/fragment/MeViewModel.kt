package cc.kafuu.bilidownload.viewmodel.fragment

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import cc.kafuu.bilidownload.view.activity.AboutActivity
import cc.kafuu.bilidownload.view.activity.LoginActivity
import cc.kafuu.bilidownload.view.activity.PersonalDetailsActivity
import cc.kafuu.bilidownload.view.dialog.ConfirmDialog
import kotlinx.coroutines.launch

class MeViewModel : CoreViewModel() {
    private val mClearingDataLiveData = MutableLiveData(false)
    val clearingDataLiveData = mClearingDataLiveData.liveData()

    fun jumpLoginOrAccount() {
        val accountData = AccountManager.accountLiveData.value ?: kotlin.run {
            startActivity(LoginActivity::class.java)
            return
        }
        startActivity(
            PersonalDetailsActivity::class.java,
            PersonalDetailsActivity.buildIntent(accountData.mid)
        )
    }

    fun jumpAbout() {
        startActivity(AboutActivity::class.java)
    }

    fun clearData() {
        if (mClearingDataLiveData.value == true) return
        ConfirmDialog.buildDialog(
            CommonLibs.getString(R.string.tv_clear_data),
            CommonLibs.getString(R.string.clear_data_message),
            CommonLibs.getString(R.string.text_cancel),
            CommonLibs.getString(R.string.text_clear_data),
            rightButtonStyle = ConfirmDialog.Companion.ButtonStyle.ClearData
        ).also { dialog ->

            popDialog(dialog) { if (it is Boolean && it) doClearData() }
        }
    }

    private fun doClearData() = viewModelScope.launch {
        mClearingDataLiveData.value = true
        DownloadRepository.queryDownloadTasksDetails(
            listOf(TaskStatus.COMPLETED, TaskStatus.SYNTHESIS_FAILED, TaskStatus.DOWNLOAD_FAILED)
        ).forEach {
            DownloadRepository.deleteDownloadTask(it.downloadTask.id)
        }
        mClearingDataLiveData.value = false
    }

    fun tryLogout() {
        ConfirmDialog.buildDialog(
            CommonLibs.getString(R.string.text_logout),
            CommonLibs.getString(R.string.logout_confirm_message),
            CommonLibs.getString(R.string.text_cancel),
            CommonLibs.getString(R.string.text_confirm),
            rightButtonStyle = ConfirmDialog.Companion.ButtonStyle.Logout
        ).also { dialog ->
            popDialog(dialog) {
                if (it is Boolean && it) AccountManager.logout()
            }
        }
    }
}