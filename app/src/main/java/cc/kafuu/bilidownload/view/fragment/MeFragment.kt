package cc.kafuu.bilidownload.view.fragment

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreFragment
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder
import cc.kafuu.bilidownload.databinding.FragmentMeBinding
import cc.kafuu.bilidownload.viewmodel.fragment.MeViewModel

class MeFragment : CoreFragment<FragmentMeBinding, MeViewModel>(
    MeViewModel::class.java,
    R.layout.fragment_me,
    BR.viewModel
) {
    override fun initViews() {

    }

    companion object {
        @JvmStatic
        fun getBuilder() = CoreFragmentBuilder(MeFragment::class)
    }
}