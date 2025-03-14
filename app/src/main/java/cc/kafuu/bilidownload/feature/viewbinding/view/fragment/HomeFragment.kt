package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragment
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.databinding.FragmentHomeBinding
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.HomeViewModel
import com.google.android.material.tabs.TabLayoutMediator

class HomeFragment : CoreFragment<FragmentHomeBinding, HomeViewModel>(
    HomeViewModel::class.java,
    R.layout.fragment_home,
    BR.viewModel
) {
    companion object {
        object Builder : CoreFragmentBuilder<HomeFragment>() {
            override fun onMallocFragment() = HomeFragment()
        }

        @JvmStatic
        fun builder() = Builder
    }

    override fun initViews() {
        val fragments = getFragmentBuilders()
        mViewDataBinding.vp2Home.apply {
            adapter = FragmentAdapter(childFragmentManager, lifecycle, fragments.map { it.second })
        }
        TabLayoutMediator(
            mViewDataBinding.tlPageSelector,
            mViewDataBinding.vp2Home
        ) { tab, position -> tab.text = CommonLibs.getString(fragments[position].first) }.attach()
    }

    private fun getFragmentBuilders() = listOf(
        R.string.home_tab_text_all to DownloadHistoryFragment.builder(
            *TaskStatus.entries.toTypedArray()
        ),
        R.string.home_tab_text_completed to DownloadHistoryFragment.builder(
            TaskStatus.COMPLETED
        ),
        R.string.home_tab_text_running to DownloadHistoryFragment.builder(
            TaskStatus.PREPARE,
            TaskStatus.DOWNLOADING,
            TaskStatus.SYNTHESIS,
        ),
        R.string.home_tab_text_failed to DownloadHistoryFragment.builder(
            TaskStatus.DOWNLOAD_FAILED,
            TaskStatus.SYNTHESIS_FAILED,
        ),
    )
}