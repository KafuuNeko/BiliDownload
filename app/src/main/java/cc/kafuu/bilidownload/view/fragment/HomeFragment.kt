package cc.kafuu.bilidownload.view.fragment

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreFragment
import cc.kafuu.bilidownload.databinding.FragmentHomeBinding
import cc.kafuu.bilidownload.viewmodel.fragment.HomeViewModel
import com.google.android.material.tabs.TabLayout

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
        mViewDataBinding.tlPageSelector.initPageSelector()
    }

    private fun TabLayout.initPageSelector() {
        addTab(newTab().setText("全部"))
        addTab(newTab().setText("已完成"))
        addTab(newTab().setText("进行中"))
    }
}