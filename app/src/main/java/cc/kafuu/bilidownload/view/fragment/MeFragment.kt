package cc.kafuu.bilidownload.view.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreFragment
import cc.kafuu.bilidownload.databinding.FragmentHomeBinding
import cc.kafuu.bilidownload.databinding.FragmentMeBinding
import cc.kafuu.bilidownload.viewmodel.HomeViewModel
import cc.kafuu.bilidownload.viewmodel.MeViewModel

class MeFragment : CoreFragment<FragmentMeBinding, MeViewModel>(
    MeViewModel::class.java,
    R.layout.fragment_me,
    BR.viewModel
) {
    override fun initViews() {

    }

    companion object {
        @JvmStatic
        fun newInstance() = MeFragment().apply {
        }
    }
}