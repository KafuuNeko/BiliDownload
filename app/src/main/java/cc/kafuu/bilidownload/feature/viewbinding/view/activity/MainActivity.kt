package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.os.Bundle
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.constant.MainTabType
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.model.event.MainTabSwitchEvent
import cc.kafuu.bilidownload.databinding.ActivityMainBinding
import cc.kafuu.bilidownload.service.DownloadService
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.HomeFragment
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.MeFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    companion object {
        private val mCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        mCoroutineScope.cancel()
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
            if (mViewDataBinding.vp2Content.currentItem !== position) {
                mViewDataBinding.vp2Content.setCurrentItem(position, false)
            }
        }
        mCoroutineScope.launch { DownloadService.resumeDownload(this@MainActivity) }
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