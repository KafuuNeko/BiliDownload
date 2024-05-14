package cc.kafuu.bilidownload.view.activity

import android.os.Bundle
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.databinding.ActivityMainBinding
import cc.kafuu.bilidownload.common.model.MainTabType
import cc.kafuu.bilidownload.common.model.event.MainTabSwitchEvent
import cc.kafuu.bilidownload.service.DownloadService
import cc.kafuu.bilidownload.view.fragment.HomeFragment
import cc.kafuu.bilidownload.view.fragment.MeFragment
import cc.kafuu.bilidownload.viewmodel.activity.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        private val mScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: MainTabSwitchEvent) {
        mViewModel.doChangeTabPosition(event.mainTabType)
    }
}