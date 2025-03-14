package cc.kafuu.bilidownload.feature.viewbinding.view.fragment

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragment
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.databinding.FragmentMeBinding
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.MeViewModel

class MeFragment : CoreFragment<FragmentMeBinding, MeViewModel>(
    MeViewModel::class.java,
    R.layout.fragment_me,
    BR.viewModel
) {
    companion object {
        object Builder : CoreFragmentBuilder<MeFragment>() {
            override fun onMallocFragment() = MeFragment()
        }

        @JvmStatic
        fun builder() = Builder
    }

    override fun initViews() {
    }
}