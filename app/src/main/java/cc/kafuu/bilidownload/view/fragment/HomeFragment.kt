package cc.kafuu.bilidownload.view.fragment

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.core.CoreFragment
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.databinding.FragmentHomeBinding
import cc.kafuu.bilidownload.viewmodel.fragment.HomeViewModel
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
        val fragments = getFragments()
        mViewDataBinding.vp2Home.apply {
            adapter = FragmentAdapter(childFragmentManager, lifecycle, fragments.map { it.second })
        }
        TabLayoutMediator(
            mViewDataBinding.tlPageSelector,
            mViewDataBinding.vp2Home
        ) { tab, position -> tab.text = fragments[position].first }.attach()
    }

    private fun getFragments() = listOf(
        CommonLibs.getString(R.string.home_tab_text_all) to HistoryFragment.builder(
            *TaskStatus.entries.toTypedArray()
        ),
        CommonLibs.getString(R.string.home_tab_text_completed) to HistoryFragment.builder(
            TaskStatus.COMPLETED
        ),
        CommonLibs.getString(R.string.home_tab_text_running) to HistoryFragment.builder(
            TaskStatus.PREPARE,
            TaskStatus.DOWNLOADING,
            TaskStatus.SYNTHESIS,
        ),
        CommonLibs.getString(R.string.home_tab_text_failed) to HistoryFragment.builder(
            TaskStatus.DOWNLOAD_FAILED,
            TaskStatus.SYNTHESIS_FAILED,
        ),
    )
}