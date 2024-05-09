package cc.kafuu.bilidownload.view.activity

import android.util.Log
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamData
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.utils.PermissionUtils
import cc.kafuu.bilidownload.databinding.ActivityMainBinding
import cc.kafuu.bilidownload.model.MainTabType
import cc.kafuu.bilidownload.service.DownloadService
import cc.kafuu.bilidownload.view.fragment.HomeFragment
import cc.kafuu.bilidownload.view.fragment.MeFragment
import cc.kafuu.bilidownload.viewmodel.activity.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Suppress("DEPRECATED_IDENTITY_EQUALS")
class MainActivity : CoreActivity<ActivityMainBinding, MainViewModel>(
    MainViewModel::class.java,
    R.layout.activity_main,
    BR.viewModel
) {
    companion object {
        private val mScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    override fun initViews() {
        setImmersionStatusBar()
        mViewDataBinding.vp2Content.apply {
            adapter = FragmentAdapter(supportFragmentManager, lifecycle).apply {
                addFragmentView(getFragments())
            }
            isUserInputEnabled = false
            currentItem = MainTabType.TAB_HOME
        }
        mViewDataBinding.rbHome.apply {
            isChecked = true
        }
        mViewModel.tabPositionLiveData.observe(this) { position ->
            if (mViewDataBinding.vp2Content.currentItem !== position) {
                mViewDataBinding.vp2Content.setCurrentItem(position, false)
            }
        }
        mScope.launch { DownloadService.resumeDownload(this@MainActivity) }
    }

    private fun getFragments() = listOf(
        HomeFragment.newInstance(),
        MeFragment.newInstance()
    )
}