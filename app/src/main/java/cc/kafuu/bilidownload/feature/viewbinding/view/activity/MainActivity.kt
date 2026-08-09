package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.constant.MainTabType
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.event.MainTabSwitchEvent
import cc.kafuu.bilidownload.common.utils.LocalNetworkPermissionRequester
import cc.kafuu.bilidownload.databinding.ActivityMainBinding
import cc.kafuu.bilidownload.service.DownloadService
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.HomeFragment
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.MeFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.MainViewModel
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@Suppress("DEPRECATED_IDENTITY_EQUALS")
class MainActivity : CoreActivity<ActivityMainBinding, MainViewModel>(
    MainViewModel::class.java,
    R.layout.activity_main,
    BR.viewModel
) {
    private var mDownloadResumeStarted = false
    private val mLocalNetworkPermissionRequester = LocalNetworkPermissionRequester(
        this,
        ::onLocalNetworkPermissionCheckCompleted
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        mLocalNetworkPermissionRequester.check(
            AppModel.downloadSourceMode,
            AppModel.downloadSourceCustomHost
        )
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun initViews() {
        setImmersionStatusBar()
        mViewDataBinding.vp2Content.apply {
            adapter = FragmentAdapter(supportFragmentManager, lifecycle, getFragments())
            isUserInputEnabled = false
            currentItem = MainTabType.TAB_HOME
        }
        mViewDataBinding.rbHome.apply {
            isChecked = true
        }
        mViewModel.tabPositionLiveData.observe(this) { position ->
            if (mViewDataBinding.vp2Content.currentItem != position) {
                mViewDataBinding.vp2Content.setCurrentItem(position, false)
            }
        }
    }

    private fun onLocalNetworkPermissionCheckCompleted(granted: Boolean) {
        if (mDownloadResumeStarted) return
        mDownloadResumeStarted = true
        if (!granted) {
            Toast.makeText(
                this,
                R.string.settings_local_network_permission_denied,
                Toast.LENGTH_SHORT
            ).show()
        }
        lifecycleScope.launch {
            DownloadService.resumeDownload(applicationContext)
        }
    }

    private fun getFragments() = listOf(
        HomeFragment.builder(),
        MeFragment.builder()
    )

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: MainTabSwitchEvent) {
        mViewModel.doChangeTabPosition(event.mainTabType)
    }
}
