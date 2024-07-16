package cc.kafuu.bilidownload.view.activity

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.databinding.ActivityPersonalDetailsBinding
import cc.kafuu.bilidownload.viewmodel.activity.PersonalDetailsViewModel

class PersonalDetailsActivity :
    CoreActivity<ActivityPersonalDetailsBinding, PersonalDetailsViewModel>(
        PersonalDetailsViewModel::class.java,
        R.layout.activity_personal_details,
        BR.viewModel
    ) {
    override fun initViews() {
        mViewDataBinding.initViews()
    }

    private fun ActivityPersonalDetailsBinding.initViews() {

    }
}