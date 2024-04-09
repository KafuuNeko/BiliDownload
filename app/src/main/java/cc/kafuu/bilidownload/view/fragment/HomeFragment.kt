package cc.kafuu.bilidownload.view.fragment

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.core.CoreFragment
import cc.kafuu.bilidownload.databinding.FragmentHomeBinding
import cc.kafuu.bilidownload.viewmodel.fragment.HomeViewModel
import com.google.android.material.tabs.TabLayoutMediator

class HomeFragment : CoreFragment<FragmentHomeBinding, HomeViewModel>(
    HomeViewModel::class.java,
    R.layout.fragment_home,
    BR.viewModel
) {
    companion object {
        @JvmStatic
        fun newInstance() = HomeFragment().apply {
        }
    }

    override fun initViews() {
        mViewDataBinding.vp2Home.apply {
            adapter = FragmentAdapter(activity?.supportFragmentManager!!, lifecycle).apply {
                addFragmentView(
                    listOf(
                        HistoryFragment.newInstance(),
                        HistoryFragment.newInstance(),
                        HistoryFragment.newInstance()
                    )
                )
            }
        }
        TabLayoutMediator(
            mViewDataBinding.tlPageSelector,
            mViewDataBinding.vp2Home
        ) { tab, position ->
            when (position) {
                0 -> tab.text = "全部"
                1 -> tab.text = "已完成"
                2 -> tab.text = "进行中"
            }
        }.attach()
    }
}